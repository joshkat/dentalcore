package com.dentalcore.appointments.internal.service;

import com.dentalcore.appointments.internal.entity.ProviderHours;
import com.dentalcore.appointments.internal.entity.ProviderTimeOff;
import com.dentalcore.appointments.internal.repository.ProviderHoursRepository;
import com.dentalcore.appointments.internal.repository.ProviderTimeOffRepository;
import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AvailabilityService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ProviderHoursRepository hoursRepository;
    private final ProviderTimeOffRepository timeOffRepository;
    private final ProviderApi providerApi;
    private final ClinicTimeService clinicTime;

    public AvailabilityService(ProviderHoursRepository hoursRepository,
                               ProviderTimeOffRepository timeOffRepository,
                               ProviderApi providerApi,
                               ClinicTimeService clinicTime) {
        this.hoursRepository = hoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.providerApi = providerApi;
        this.clinicTime = clinicTime;
    }

    public record HoursBlock(@NotNull Integer dayOfWeek, @NotNull LocalTime startTime,
                             @NotNull LocalTime endTime) {
    }

    public record TimeOffRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                 String reason) {
    }

    public record TimeOffResponse(UUID id, Instant startsAt, Instant endsAt, String reason) {
    }

    @Transactional(readOnly = true)
    public List<HoursBlock> hoursFor(UUID providerId) {
        return hoursRepository.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(providerId).stream()
                .map(h -> new HoursBlock(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()))
                .toList();
    }

    public List<HoursBlock> replaceHours(UUID providerId, List<HoursBlock> blocks) {
        requireProvider(providerId);
        for (HoursBlock block : blocks) {
            if (block.dayOfWeek() < 1 || block.dayOfWeek() > 7) {
                throw new InvalidRequestException("dayOfWeek must be 1 (Mon) to 7 (Sun)");
            }
            if (!block.endTime().isAfter(block.startTime())) {
                throw new InvalidRequestException("Hours block must end after it starts");
            }
        }
        hoursRepository.deleteByProviderId(providerId);
        hoursRepository.saveAll(blocks.stream()
                .map(b -> new ProviderHours(providerId, b.dayOfWeek(), b.startTime(), b.endTime()))
                .toList());
        return hoursFor(providerId);
    }

    @Transactional(readOnly = true)
    public List<TimeOffResponse> timeOffFor(UUID providerId) {
        return timeOffRepository.findByProviderIdOrderByStartsAtDesc(providerId).stream()
                .map(t -> new TimeOffResponse(t.getId(), t.getStartsAt(), t.getEndsAt(),
                        t.getReason()))
                .toList();
    }

    public TimeOffResponse addTimeOff(UUID providerId, TimeOffRequest request) {
        requireProvider(providerId);
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidRequestException("Time off must end after it starts");
        }
        ProviderTimeOff saved = timeOffRepository.save(new ProviderTimeOff(
                providerId, request.startsAt(), request.endsAt(), request.reason()));
        return new TimeOffResponse(saved.getId(), saved.getStartsAt(), saved.getEndsAt(),
                saved.getReason());
    }

    public void removeTimeOff(UUID providerId, UUID timeOffId) {
        ProviderTimeOff timeOff = timeOffRepository.findById(timeOffId)
                .filter(t -> t.getProviderId().equals(providerId))
                .orElseThrow(() -> new ResourceNotFoundException("Time off", timeOffId));
        timeOffRepository.delete(timeOff);
    }

    /**
     * Booking guard: within working hours (when a weekly template exists — no
     * template means always bookable) and not during time off.
     */
    @Transactional(readOnly = true)
    public void requireAvailable(UUID providerId, Instant startsAt, Instant endsAt) {
        List<ProviderTimeOff> conflicts =
                timeOffRepository.findOverlapping(providerId, startsAt, endsAt);
        if (!conflicts.isEmpty()) {
            String reason = conflicts.get(0).getReason();
            throw new InvalidRequestException("The provider is unavailable then"
                    + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        }

        List<ProviderHours> template =
                hoursRepository.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(providerId);
        if (template.isEmpty()) {
            return;
        }
        ZoneId zone = clinicTime.clinicZone(DEFAULT_CLINIC_ID);
        ZonedDateTime localStart = startsAt.atZone(zone);
        ZonedDateTime localEnd = endsAt.atZone(zone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            throw new InvalidRequestException("Appointments cannot span multiple days");
        }
        int day = localStart.getDayOfWeek().getValue();
        boolean inHours = template.stream().anyMatch(block ->
                block.getDayOfWeek() == day
                        && !localStart.toLocalTime().isBefore(block.getStartTime())
                        && !localEnd.toLocalTime().isAfter(block.getEndTime()));
        if (!inHours) {
            throw new InvalidRequestException(
                    "Outside the provider's working hours (%s %s)".formatted(
                            localStart.getDayOfWeek(),
                            localStart.toLocalTime() + "–" + localEnd.toLocalTime()));
        }
    }

    private void requireProvider(UUID providerId) {
        if (providerApi.findSummary(providerId).isEmpty()) {
            throw new InvalidRequestException("Unknown provider");
        }
    }
}
