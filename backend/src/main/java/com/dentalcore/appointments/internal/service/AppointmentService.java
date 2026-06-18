package com.dentalcore.appointments.internal.service;

import com.dentalcore.appointments.api.AppointmentApi;
import com.dentalcore.appointments.api.AppointmentCompletedEvent;
import com.dentalcore.appointments.api.AppointmentView;
import com.dentalcore.appointments.internal.dto.AppointmentRequest;
import com.dentalcore.appointments.internal.dto.AppointmentResponse;
import com.dentalcore.appointments.internal.entity.Appointment;
import com.dentalcore.appointments.internal.entity.AppointmentProcedure;
import com.dentalcore.appointments.internal.entity.AppointmentStatus;
import com.dentalcore.appointments.internal.entity.Operatory;
import com.dentalcore.appointments.internal.repository.AppointmentProcedureRepository;
import com.dentalcore.appointments.internal.repository.AppointmentRepository;
import com.dentalcore.appointments.internal.repository.OperatoryRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.procedures.api.CompletedProcedureApi;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.providers.api.ProviderSummary;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppointmentService implements AppointmentApi {

    private static final String ENTITY_TYPE = "Appointment";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AppointmentRepository appointmentRepository;
    private final OperatoryRepository operatoryRepository;
    private final AppointmentProcedureRepository procedureLinkRepository;
    private final PatientApi patientApi;
    private final ProviderApi providerApi;
    private final ProcedureCatalogApi catalogApi;
    private final CompletedProcedureApi completedProcedureApi;
    private final AvailabilityService availabilityService;
    private final BlockoutService blockoutService;
    private final com.dentalcore.infrastructure.time.ClinicTimeService clinicTime;
    private final com.dentalcore.shared.notifications.NotificationPort notifications;
    private final ApplicationEventPublisher events;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              OperatoryRepository operatoryRepository,
                              AppointmentProcedureRepository procedureLinkRepository,
                              PatientApi patientApi,
                              ProviderApi providerApi,
                              ProcedureCatalogApi catalogApi,
                              CompletedProcedureApi completedProcedureApi,
                              AvailabilityService availabilityService,
                              BlockoutService blockoutService,
                              com.dentalcore.infrastructure.time.ClinicTimeService clinicTime,
                              com.dentalcore.shared.notifications.NotificationPort notifications,
                              ApplicationEventPublisher events) {
        this.appointmentRepository = appointmentRepository;
        this.operatoryRepository = operatoryRepository;
        this.procedureLinkRepository = procedureLinkRepository;
        this.patientApi = patientApi;
        this.providerApi = providerApi;
        this.catalogApi = catalogApi;
        this.completedProcedureApi = completedProcedureApi;
        this.availabilityService = availabilityService;
        this.blockoutService = blockoutService;
        this.clinicTime = clinicTime;
        this.notifications = notifications;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<AppointmentView> findView(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId).map(a -> new AppointmentView(
                a.getId(), a.getClinicId(), a.getPatientId(), a.getProviderId(),
                a.getStartsAt(), a.getEndsAt(), a.getStatus().name()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Instant from, Instant to, UUID providerId,
                                          UUID operatoryId, UUID patientId) {
        if (!to.isAfter(from)) {
            throw new InvalidRequestException("'to' must be after 'from'");
        }
        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThan(root.get("startsAt"), to));
            predicates.add(cb.greaterThan(root.get("endsAt"), from));
            if (providerId != null) {
                predicates.add(cb.equal(root.get("providerId"), providerId));
            }
            if (operatoryId != null) {
                predicates.add(cb.equal(root.get("operatoryId"), operatoryId));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Appointment> appointments =
                appointmentRepository.findAll(spec, Sort.by("startsAt"));
        return toResponses(appointments);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        return toResponses(List.of(findAppointment(id))).get(0);
    }

    public AppointmentResponse create(AppointmentRequest request) {
        validateReferences(request);
        validateTimes(request.startsAt(), request.endsAt());
        availabilityService.requireAvailable(request.providerId(),
                request.startsAt(), request.endsAt());
        requireNoConflicts(request.providerId(), request.operatoryId(),
                request.startsAt(), request.endsAt(), null);
        blockoutService.requireNoBlockout(request.operatoryId(),
                request.startsAt(), request.endsAt());

        Appointment appointment = new Appointment(
                DEFAULT_CLINIC_ID, request.patientId(), request.providerId(),
                request.operatoryId(), request.startsAt(), request.endsAt());
        appointment.updateDetails(request.notes(), request.colorOverride(),
                request.asapOrDefault());
        appointment = saveGuardedAgainstOverlap(appointment);

        publishAudit(appointment.getId(), AuditEvent.AuditAction.CREATE, null, Map.of(
                "patientId", request.patientId().toString(),
                "startsAt", request.startsAt().toString()));
        return toResponses(List.of(appointment)).get(0);
    }

    public record RecurringResult(UUID seriesId, List<AppointmentResponse> created,
                                  List<SkippedOccurrence> skipped) {
    }

    public record SkippedOccurrence(Instant startsAt, String reason) {
    }

    /**
     * Book a recurring series from a base appointment. Occurrences that conflict
     * (double-booking, provider time off, blocked operatory, outside hours) are
     * skipped and reported rather than failing the whole series. Pre-checks run
     * before each save so a skip never poisons the surrounding transaction.
     */
    public RecurringResult createRecurring(AppointmentRequest base, String frequency,
                                           int occurrences) {
        validateReferences(base);
        validateTimes(base.startsAt(), base.endsAt());
        if (occurrences < 2 || occurrences > 52) {
            throw new InvalidRequestException("occurrences must be between 2 and 52");
        }
        if (!List.of("WEEKLY", "BIWEEKLY", "MONTHLY").contains(frequency)) {
            throw new InvalidRequestException("frequency must be WEEKLY, BIWEEKLY, or MONTHLY");
        }

        java.time.ZoneId zone = clinicTime.clinicZone(DEFAULT_CLINIC_ID);
        UUID seriesId = UUID.randomUUID();
        List<AppointmentResponse> created = new java.util.ArrayList<>();
        List<SkippedOccurrence> skipped = new java.util.ArrayList<>();

        for (int i = 0; i < occurrences; i++) {
            Instant start = shift(base.startsAt(), zone, frequency, i);
            Instant end = shift(base.endsAt(), zone, frequency, i);
            try {
                availabilityService.requireAvailable(base.providerId(), start, end);
                requireNoConflicts(base.providerId(), base.operatoryId(), start, end, null);
                blockoutService.requireNoBlockout(base.operatoryId(), start, end);
            } catch (InvalidRequestException | ConflictException ex) {
                skipped.add(new SkippedOccurrence(start, ex.getMessage()));
                continue;
            }
            Appointment appt = new Appointment(DEFAULT_CLINIC_ID, base.patientId(),
                    base.providerId(), base.operatoryId(), start, end);
            appt.updateDetails(base.notes(), base.colorOverride(), base.asapOrDefault());
            appt.assignSeries(seriesId);
            appt = appointmentRepository.saveAndFlush(appt);
            created.add(toResponses(List.of(appt)).get(0));
        }

        if (created.isEmpty()) {
            throw new InvalidRequestException("No occurrences could be booked (all conflicted)");
        }
        publishAudit(UUID.fromString(created.get(0).id().toString()),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("seriesId", seriesId.toString(), "occurrences", created.size()));
        return new RecurringResult(seriesId, created, skipped);
    }

    private Instant shift(Instant base, java.time.ZoneId zone, String frequency, int n) {
        java.time.ZonedDateTime z = base.atZone(zone);
        return switch (frequency) {
            case "WEEKLY" -> z.plusWeeks(n).toInstant();
            case "BIWEEKLY" -> z.plusWeeks(2L * n).toInstant();
            default -> z.plusMonths(n).toInstant();
        };
    }

    /** Record that a confirmation request was sent; the patient confirms via status. */
    public AppointmentResponse sendConfirmation(UUID id) {
        Appointment appointment = findAppointment(id);
        appointment.markConfirmationSent(Instant.now());
        appointmentRepository.save(appointment);
        String name = patientApi.findSummary(appointment.getPatientId())
                .map(p -> p.firstName() + " " + p.lastName())
                .orElse(appointment.getPatientId().toString());
        notifications.sendSms(name,
                "Please confirm your upcoming dental appointment.");
        publishAudit(id, AuditEvent.AuditAction.UPDATE, null,
                Map.of("confirmationSentAt", appointment.getConfirmationSentAt().toString()));
        return toResponses(List.of(appointment)).get(0);
    }

    public AppointmentResponse update(UUID id, AppointmentRequest request) {
        Appointment appointment = findAppointment(id);
        validateReferences(request);
        validateTimes(request.startsAt(), request.endsAt());
        availabilityService.requireAvailable(request.providerId(),
                request.startsAt(), request.endsAt());
        requireNoConflicts(request.providerId(), request.operatoryId(),
                request.startsAt(), request.endsAt(), id);
        blockoutService.requireNoBlockout(request.operatoryId(),
                request.startsAt(), request.endsAt());

        Map<String, Object> before = Map.of(
                "startsAt", appointment.getStartsAt().toString(),
                "providerId", appointment.getProviderId().toString(),
                "operatoryId", appointment.getOperatoryId().toString());

        appointment.reschedule(request.providerId(), request.operatoryId(),
                request.startsAt(), request.endsAt());
        appointment.updateDetails(request.notes(), request.colorOverride(),
                request.asapOrDefault());
        saveGuardedAgainstOverlap(appointment);

        publishAudit(id, AuditEvent.AuditAction.UPDATE, before, Map.of(
                "startsAt", request.startsAt().toString(),
                "providerId", request.providerId().toString(),
                "operatoryId", request.operatoryId().toString()));
        return toResponses(List.of(appointment)).get(0);
    }

    public AppointmentResponse updateStatus(UUID id, String status, String cancelReason) {
        Appointment appointment = findAppointment(id);
        AppointmentStatus target = AppointmentStatus.valueOf(status);
        String previous = appointment.getStatus().name();

        appointment.transitionTo(target, cancelReason);

        publishAudit(id, AuditEvent.AuditAction.STATUS_CHANGE,
                Map.of("status", previous), Map.of("status", status));

        if (target == AppointmentStatus.COMPLETED) {
            List<UUID> procedureCodeIds = procedureLinkRepository
                    .findByAppointmentId(appointment.getId()).stream()
                    .map(AppointmentProcedure::getProcedureCodeId)
                    .toList();
            // Record (and charge) whatever wasn't already completed during
            // checkout — this replaces the old billing auto-charge listener
            // and is what guarantees exactly one charge per procedure.
            completedProcedureApi.completeAllForAppointment(
                    appointment.getId(), appointment.getClinicId(),
                    appointment.getPatientId(), appointment.getProviderId(),
                    procedureCodeIds);
            events.publishEvent(new AppointmentCompletedEvent(
                    appointment.getId(), appointment.getClinicId(), appointment.getPatientId(),
                    appointment.getProviderId(), procedureCodeIds, Instant.now()));
        }
        return toResponses(List.of(appointment)).get(0);
    }

    public AppointmentResponse setProcedures(UUID id, List<UUID> procedureCodeIds) {
        Appointment appointment = findAppointment(id);
        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new InvalidRequestException(
                    "A %s appointment cannot be modified".formatted(appointment.getStatus()));
        }
        Set<UUID> uniqueIds = Set.copyOf(procedureCodeIds);
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(uniqueIds);
        for (UUID codeId : uniqueIds) {
            ProcedureSummary entry = catalog.get(codeId);
            if (entry == null || !entry.active()) {
                throw new InvalidRequestException("Unknown or inactive procedure code: " + codeId);
            }
        }
        procedureLinkRepository.deleteByAppointmentId(id);
        procedureLinkRepository.saveAll(
                uniqueIds.stream().map(codeId -> new AppointmentProcedure(id, codeId)).toList());

        publishAudit(id, AuditEvent.AuditAction.UPDATE, null, Map.of(
                "procedures", catalog.values().stream().map(ProcedureSummary::code).toList()));
        return toResponses(List.of(appointment)).get(0);
    }

    // ---- helpers ----

    private void validateReferences(AppointmentRequest request) {
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        if (!providerApi.existsAndActive(request.providerId())) {
            throw new InvalidRequestException("Unknown or inactive provider");
        }
        Operatory operatory = operatoryRepository.findById(request.operatoryId())
                .orElseThrow(() -> new InvalidRequestException("Unknown operatory"));
        if (!operatory.isActive()) {
            throw new InvalidRequestException("Operatory is inactive");
        }
    }

    private void validateTimes(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidRequestException("Appointment must end after it starts");
        }
    }

    private void requireNoConflicts(UUID providerId, UUID operatoryId,
                                    Instant startsAt, Instant endsAt, UUID excludeId) {
        List<Appointment> conflicts = appointmentRepository
                .findOverlapping(providerId, operatoryId, startsAt, endsAt).stream()
                .filter(a -> !a.getId().equals(excludeId))
                .toList();
        if (conflicts.isEmpty()) {
            return;
        }
        boolean providerBusy = conflicts.stream()
                .anyMatch(a -> a.getProviderId().equals(providerId));
        throw new ConflictException(providerBusy
                ? "The provider already has an appointment in this time slot"
                : "The operatory is already booked in this time slot");
    }

    /**
     * Even after {@link #requireNoConflicts} passes, a concurrent booking can
     * land first and trip the GiST exclusion constraints at flush time.
     * Flushing here turns that race into the same 409 the pre-check produces
     * instead of surfacing a commit-time 500.
     */
    private Appointment saveGuardedAgainstOverlap(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("no_provider_overlap")) {
                throw new ConflictException(
                        "The provider already has an appointment in this time slot");
            }
            if (cause.contains("no_operatory_overlap")) {
                throw new ConflictException(
                        "The operatory is already booked in this time slot");
            }
            throw e;
        }
    }

    private Appointment findAppointment(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    private void publishAudit(UUID appointmentId, AuditEvent.AuditAction action,
                              Map<String, Object> before, Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, appointmentId, action, before, after));
    }

    private List<AppointmentResponse> toResponses(List<Appointment> appointments) {
        Set<UUID> patientIds = appointments.stream()
                .map(Appointment::getPatientId).collect(Collectors.toSet());
        Set<UUID> providerIds = appointments.stream()
                .map(Appointment::getProviderId).collect(Collectors.toSet());
        Map<UUID, PatientSummary> patients = patientApi.findSummaries(patientIds);
        Map<UUID, ProviderSummary> providers = providerApi.findSummaries(providerIds);
        Map<UUID, String> operatoryNames = operatoryRepository.findAll().stream()
                .collect(Collectors.toMap(Operatory::getId, Operatory::getName));

        List<AppointmentProcedure> links = procedureLinkRepository.findByAppointmentIdIn(
                appointments.stream().map(Appointment::getId).toList());
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                links.stream().map(AppointmentProcedure::getProcedureCodeId)
                        .collect(Collectors.toSet()));
        Map<UUID, List<AppointmentResponse.ProcedureDto>> proceduresByAppointment =
                links.stream().collect(Collectors.groupingBy(
                        AppointmentProcedure::getAppointmentId,
                        Collectors.mapping(link -> {
                            ProcedureSummary entry = catalog.get(link.getProcedureCodeId());
                            return new AppointmentResponse.ProcedureDto(
                                    link.getProcedureCodeId(),
                                    entry != null ? entry.code() : null,
                                    entry != null ? entry.description() : null,
                                    entry != null ? entry.standardFee() : null);
                        }, Collectors.toList())));

        return appointments.stream().map(a -> {
            PatientSummary patient = patients.get(a.getPatientId());
            ProviderSummary provider = providers.get(a.getProviderId());
            String color = a.getColorOverride() != null
                    ? a.getColorOverride()
                    : provider != null ? provider.color() : "#3b82f6";
            return new AppointmentResponse(
                    a.getId(),
                    a.getPatientId(),
                    patient != null ? patient.firstName() : null,
                    patient != null ? patient.lastName() : null,
                    a.getProviderId(),
                    provider != null ? provider.firstName() : null,
                    provider != null ? provider.lastName() : null,
                    a.getOperatoryId(),
                    operatoryNames.get(a.getOperatoryId()),
                    a.getStartsAt(),
                    a.getEndsAt(),
                    a.getStatus().name(),
                    a.getNotes(),
                    color,
                    a.getCancelledReason(),
                    a.isAsap(),
                    a.getSeriesId(),
                    a.getConfirmationSentAt(),
                    proceduresByAppointment.getOrDefault(a.getId(), List.of()),
                    a.getCreatedAt(),
                    a.getUpdatedAt());
        }).toList();
    }
}
