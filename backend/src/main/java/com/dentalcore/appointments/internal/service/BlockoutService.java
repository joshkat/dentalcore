package com.dentalcore.appointments.internal.service;

import com.dentalcore.appointments.internal.entity.ScheduleBlockout;
import com.dentalcore.appointments.internal.repository.OperatoryRepository;
import com.dentalcore.appointments.internal.repository.ScheduleBlockoutRepository;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Manages operatory blockouts — spans during which a chair cannot be booked. */
@Service
@Transactional
public class BlockoutService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ScheduleBlockoutRepository blockoutRepository;
    private final OperatoryRepository operatoryRepository;

    public BlockoutService(ScheduleBlockoutRepository blockoutRepository,
                           OperatoryRepository operatoryRepository) {
        this.blockoutRepository = blockoutRepository;
        this.operatoryRepository = operatoryRepository;
    }

    public record BlockoutRequest(@NotNull UUID operatoryId, @NotNull Instant startsAt,
                                  @NotNull Instant endsAt, String reason) {
    }

    public record BlockoutResponse(UUID id, UUID operatoryId, String operatoryName,
                                   Instant startsAt, Instant endsAt, String reason) {
    }

    @Transactional(readOnly = true)
    public List<BlockoutResponse> list(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new InvalidRequestException("'to' must be after 'from'");
        }
        var names = operatoryRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(o -> o.getId(), o -> o.getName()));
        return blockoutRepository.findInRange(from, to).stream()
                .map(b -> new BlockoutResponse(b.getId(), b.getOperatoryId(),
                        names.get(b.getOperatoryId()), b.getStartsAt(), b.getEndsAt(),
                        b.getReason()))
                .toList();
    }

    public BlockoutResponse create(BlockoutRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidRequestException("Blockout must end after it starts");
        }
        var operatory = operatoryRepository.findById(request.operatoryId())
                .orElseThrow(() -> new InvalidRequestException("Unknown operatory"));
        ScheduleBlockout saved = blockoutRepository.save(new ScheduleBlockout(
                DEFAULT_CLINIC_ID, request.operatoryId(), request.startsAt(), request.endsAt(),
                request.reason()));
        return new BlockoutResponse(saved.getId(), saved.getOperatoryId(), operatory.getName(),
                saved.getStartsAt(), saved.getEndsAt(), saved.getReason());
    }

    public void delete(UUID id) {
        ScheduleBlockout blockout = blockoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blockout", id));
        blockoutRepository.delete(blockout);
    }

    /** Booking guard: reject when the operatory is blocked off for the slot. */
    @Transactional(readOnly = true)
    public void requireNoBlockout(UUID operatoryId, Instant startsAt, Instant endsAt) {
        List<ScheduleBlockout> conflicts =
                blockoutRepository.findOverlapping(operatoryId, startsAt, endsAt);
        if (!conflicts.isEmpty()) {
            String reason = conflicts.get(0).getReason();
            throw new InvalidRequestException("The operatory is blocked off then"
                    + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        }
    }
}
