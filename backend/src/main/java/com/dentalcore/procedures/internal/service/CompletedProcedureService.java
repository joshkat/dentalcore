package com.dentalcore.procedures.internal.service;

import com.dentalcore.billing.api.BillingPostingApi;
import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.procedures.api.CompletedProcedureApi;
import com.dentalcore.procedures.api.CompletedProcedureView;
import com.dentalcore.procedures.api.ProcedureCompletedEvent;
import com.dentalcore.procedures.api.ProcedureCompletionUndoneEvent;
import com.dentalcore.procedures.internal.dto.CompletedProcedureDtos.CompleteProcedureRequest;
import com.dentalcore.procedures.internal.dto.CompletedProcedureDtos.CompletedProcedureResponse;
import com.dentalcore.procedures.internal.entity.CompletedProcedure;
import com.dentalcore.procedures.internal.entity.ProcedureCode;
import com.dentalcore.procedures.internal.repository.CompletedProcedureRepository;
import com.dentalcore.procedures.internal.repository.ProcedureCodeRepository;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.providers.api.ProviderSummary;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Completed work: one row per performed procedure, with the ledger charge it
 * produced. Checkout (completing single procedures during the visit) and
 * appointment completion share the same posting path, so the appointments
 * module's completeAll only charges codes that have no completed row yet —
 * a visit can never be double-charged.
 */
@Service
@Transactional
public class CompletedProcedureService implements CompletedProcedureApi {

    private static final String ENTITY_TYPE = "CompletedProcedure";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CompletedProcedureRepository repository;
    private final ProcedureCodeRepository catalog;
    private final PatientApi patientApi;
    private final ProviderApi providerApi;
    private final BillingPostingApi billing;
    private final ClinicTimeService clinicTime;
    private final ApplicationEventPublisher events;

    public CompletedProcedureService(CompletedProcedureRepository repository,
                                     ProcedureCodeRepository catalog,
                                     PatientApi patientApi,
                                     ProviderApi providerApi,
                                     BillingPostingApi billing,
                                     ClinicTimeService clinicTime,
                                     ApplicationEventPublisher events) {
        this.repository = repository;
        this.catalog = catalog;
        this.patientApi = patientApi;
        this.providerApi = providerApi;
        this.billing = billing;
        this.clinicTime = clinicTime;
        this.events = events;
    }

    public CompletedProcedureResponse complete(CompleteProcedureRequest request) {
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        ProviderSummary provider = providerApi.findSummary(request.providerId())
                .orElseThrow(() -> new InvalidRequestException("Unknown provider"));
        ProcedureCode code = catalog.findById(request.procedureCodeId())
                .orElseThrow(() -> new InvalidRequestException("Unknown procedure code"));
        if (request.plannedProcedureId() != null
                && repository.existsByPlannedProcedureId(request.plannedProcedureId())) {
            throw new ConflictException("This planned procedure has already been completed");
        }
        CompletedProcedure completed = doComplete(
                DEFAULT_CLINIC_ID, request.patientId(), request.providerId(), code,
                request.appointmentId(), request.plannedProcedureId(),
                blankToNull(request.tooth()), blankToNull(request.surfaces()),
                request.feeOverride(), blankToNull(request.notes()));
        return toResponse(completed, code, provider);
    }

    /** Undo a same-day completion: reverse the charge, revert the plan, delete the row. */
    public void undo(UUID id) {
        CompletedProcedure completed = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Completed procedure", id));
        LocalDate today = clinicTime.today(completed.getClinicId());
        if (!today.equals(completed.getEntryDate())) {
            throw new InvalidRequestException(
                    "A completed procedure can only be undone on the day it was posted");
        }
        String code = catalog.findById(completed.getProcedureCodeId())
                .map(ProcedureCode::getCode).orElse("procedure");
        if (completed.getLedgerEntryId() != null) {
            billing.reverseEntry(completed.getLedgerEntryId(),
                    "Undo completed procedure " + code);
        }
        if (completed.getPlannedProcedureId() != null) {
            events.publishEvent(
                    new ProcedureCompletionUndoneEvent(completed.getPlannedProcedureId()));
        }
        repository.delete(completed);
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.DELETE,
                Map.of("code", code,
                        "fee", completed.getFee().toString(),
                        "patientId", completed.getPatientId().toString()),
                null));
    }

    @Transactional(readOnly = true)
    public List<CompletedProcedureResponse> list(UUID patientId, LocalDate from, LocalDate to) {
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }
        List<CompletedProcedure> rows;
        if (from != null && to != null) {
            rows = repository.findByPatientIdAndEntryDateBetweenOrderByCompletedAtDesc(
                    patientId, from, to);
        } else if (from != null) {
            rows = repository.findByPatientIdAndEntryDateGreaterThanEqualOrderByCompletedAtDesc(
                    patientId, from);
        } else if (to != null) {
            rows = repository.findByPatientIdAndEntryDateLessThanEqualOrderByCompletedAtDesc(
                    patientId, to);
        } else {
            rows = repository.findByPatientIdOrderByCompletedAtDesc(patientId);
        }
        return toResponses(rows);
    }

    // ---- CompletedProcedureApi ----

    @Override
    public void completeAllForAppointment(UUID appointmentId, UUID clinicId, UUID patientId,
                                          UUID providerId, List<UUID> procedureCodeIds) {
        for (UUID codeId : procedureCodeIds) {
            if (repository.existsByAppointmentIdAndProcedureCodeId(appointmentId, codeId)) {
                continue; // already completed during checkout — never re-charge
            }
            catalog.findById(codeId).ifPresent(code -> doComplete(
                    clinicId, patientId, providerId, code, appointmentId,
                    null, null, null, null, null));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedProcedureView> findForAppointment(UUID appointmentId) {
        List<CompletedProcedure> rows =
                repository.findByAppointmentIdOrderByCompletedAtAsc(appointmentId);
        Map<UUID, ProcedureCode> codes = codesFor(rows);
        return rows.stream().map(row -> {
            ProcedureCode code = codes.get(row.getProcedureCodeId());
            return new CompletedProcedureView(
                    row.getId(), row.getPatientId(), row.getProviderId(),
                    row.getProcedureCodeId(),
                    code != null ? code.getCode() : null,
                    code != null ? code.getDescription() : null,
                    row.getTooth(), row.getSurfaces(), row.getFee(),
                    row.getCompletedAt(), row.getEntryDate());
        }).toList();
    }

    // ---- helpers ----

    private CompletedProcedure doComplete(UUID clinicId, UUID patientId, UUID providerId,
                                          ProcedureCode code, UUID appointmentId,
                                          UUID plannedProcedureId, String tooth, String surfaces,
                                          BigDecimal feeOverride, String notes) {
        BigDecimal fee = feeOverride != null ? feeOverride : code.getStandardFee();
        Instant completedAt = Instant.now();
        LocalDate entryDate = clinicTime.today(clinicId);

        CompletedProcedure completed = repository.save(new CompletedProcedure(
                clinicId, patientId, providerId, code.getId(), appointmentId,
                plannedProcedureId, tooth, surfaces, fee, notes, completedAt, entryDate));

        if (fee.signum() > 0) {
            // EM-DASH-free so the PDFs stay trivial: 'D1110 - Prophylaxis - adult #30'
            String description = code.getCode() + " - " + code.getDescription()
                    + (tooth != null ? " #" + tooth : "");
            UUID ledgerEntryId = billing.postProcedureCharge(
                    clinicId, patientId, code.getId(), appointmentId,
                    description, fee, entryDate);
            completed.linkLedgerEntry(ledgerEntryId);
        }

        events.publishEvent(new ProcedureCompletedEvent(
                completed.getId(), clinicId, patientId, providerId, code.getId(),
                plannedProcedureId, tooth, surfaces, completedAt));
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, completed.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("code", code.getCode(),
                        "fee", fee.toString(),
                        "patientId", patientId.toString())));
        return completed;
    }

    private List<CompletedProcedureResponse> toResponses(List<CompletedProcedure> rows) {
        Map<UUID, ProcedureCode> codes = codesFor(rows);
        Map<UUID, ProviderSummary> providers = providerApi.findSummaries(
                rows.stream().map(CompletedProcedure::getProviderId)
                        .collect(Collectors.toSet()));
        return rows.stream().map(row -> toResponse(row,
                codes.get(row.getProcedureCodeId()),
                providers.get(row.getProviderId()))).toList();
    }

    private Map<UUID, ProcedureCode> codesFor(List<CompletedProcedure> rows) {
        Set<UUID> codeIds = rows.stream()
                .map(CompletedProcedure::getProcedureCodeId)
                .collect(Collectors.toSet());
        return catalog.findAllById(codeIds).stream()
                .collect(Collectors.toMap(ProcedureCode::getId, c -> c));
    }

    private CompletedProcedureResponse toResponse(CompletedProcedure row, ProcedureCode code,
                                                  ProviderSummary provider) {
        return new CompletedProcedureResponse(
                row.getId(),
                row.getPatientId(),
                row.getProviderId(),
                provider != null ? provider.firstName() : null,
                provider != null ? provider.lastName() : null,
                row.getProcedureCodeId(),
                code != null ? code.getCode() : null,
                code != null ? code.getDescription() : null,
                row.getTooth(),
                row.getSurfaces(),
                row.getFee(),
                row.getAppointmentId(),
                row.getPlannedProcedureId(),
                row.getCompletedAt(),
                row.getEntryDate());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
