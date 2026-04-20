package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.dto.BillingDtos.AdjustmentRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.ChargeRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerEntryResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentRequest;
import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BillingService {

    private static final String ENTITY_TYPE = "LedgerEntry";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final LedgerEntryRepository ledger;
    private final PatientApi patientApi;
    private final ProcedureCatalogApi catalogApi;
    private final ApplicationEventPublisher events;
    private final com.dentalcore.infrastructure.time.ClinicTimeService clinicTime;

    public BillingService(LedgerEntryRepository ledger,
                          PatientApi patientApi,
                          ProcedureCatalogApi catalogApi,
                          ApplicationEventPublisher events,
                          com.dentalcore.infrastructure.time.ClinicTimeService clinicTime) {
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.catalogApi = catalogApi;
        this.events = events;
        this.clinicTime = clinicTime;
    }

    /** Every entry is stamped with the clinic-local business date. */
    private LedgerEntry post(LedgerEntry entry) {
        return ledger.save(entry.at(clinicTime.today(DEFAULT_CLINIC_ID)));
    }

    @Transactional(readOnly = true)
    public LedgerResponse ledgerFor(UUID patientId, Pageable pageable) {
        requirePatient(patientId);
        Page<LedgerEntry> page =
                ledger.findByPatientIdOrderByEntryDateDescCreatedAtDesc(patientId, pageable);
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                page.getContent().stream()
                        .map(LedgerEntry::getProcedureCodeId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()));
        Set<UUID> reversedIds = page.getContent().stream()
                .map(LedgerEntry::getId)
                .filter(ledger::existsByReversalOf)
                .collect(Collectors.toSet());

        return new LedgerResponse(
                ledger.balanceFor(patientId),
                page.getContent().stream().map(entry -> toResponse(entry,
                        catalog.get(entry.getProcedureCodeId()),
                        reversedIds.contains(entry.getId()))).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceFor(UUID patientId) {
        requirePatient(patientId);
        return ledger.balanceFor(patientId);
    }

    public LedgerEntryResponse postCharge(ChargeRequest request) {
        requirePatient(request.patientId());
        ProcedureSummary procedure = null;
        BigDecimal amount = request.amount();
        String description = request.description();
        if (request.procedureCodeId() != null) {
            procedure = catalogApi.findSummary(request.procedureCodeId())
                    .orElseThrow(() -> new InvalidRequestException("Unknown procedure code"));
            if (amount == null) {
                amount = procedure.standardFee();
            }
            if (description == null || description.isBlank()) {
                description = procedure.code() + " — " + procedure.description();
            }
        }
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("A charge needs a positive amount or a procedure code");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidRequestException("A description is required for manual charges");
        }

        LedgerEntry entry = post(LedgerEntry.charge(
                DEFAULT_CLINIC_ID, request.patientId(), amount, description,
                procedure != null ? procedure.id() : null, null,
                CurrentUser.id().orElse(null)));
        audit(entry, "chargePosted");
        return toResponse(entry, procedure, false);
    }

    public LedgerEntryResponse recordPayment(PaymentRequest request) {
        requirePatient(request.patientId());
        LedgerEntry entry = post(LedgerEntry.payment(
                DEFAULT_CLINIC_ID, request.patientId(), request.amount(),
                LedgerEntry.Method.valueOf(request.method()),
                request.description() == null || request.description().isBlank()
                        ? "Patient payment (" + request.method() + ")"
                        : request.description(),
                CurrentUser.id().orElse(null)));
        audit(entry, "paymentRecorded");
        return toResponse(entry, null, false);
    }

    public LedgerEntryResponse postAdjustment(AdjustmentRequest request) {
        requirePatient(request.patientId());
        if (request.amount().signum() == 0) {
            throw new InvalidRequestException("Adjustment amount cannot be zero");
        }
        LedgerEntry entry = post(LedgerEntry.adjustment(
                DEFAULT_CLINIC_ID, request.patientId(), request.amount(),
                request.description(), CurrentUser.id().orElse(null)));
        audit(entry, "adjustmentPosted");
        return toResponse(entry, null, false);
    }

    public LedgerEntryResponse reverse(UUID entryId, String reason) {
        LedgerEntry original = ledger.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger entry", entryId));
        if (original.getReversalOf() != null) {
            throw new InvalidRequestException("A reversal cannot be reversed");
        }
        if (ledger.existsByReversalOf(entryId)) {
            throw new ConflictException("This entry has already been reversed");
        }
        LedgerEntry reversal = post(LedgerEntry.reversalOf(
                original, reason, CurrentUser.id().orElse(null)));
        audit(reversal, "entryReversed");
        return toResponse(reversal, null, false);
    }

    // ---- internal posting used by event listeners ----

    void postAutoCharge(UUID patientId, UUID appointmentId, ProcedureSummary procedure) {
        LedgerEntry entry = post(LedgerEntry.charge(
                DEFAULT_CLINIC_ID, patientId, procedure.standardFee(),
                procedure.code() + " — " + procedure.description(),
                procedure.id(), appointmentId, CurrentUser.id().orElse(null)));
        audit(entry, "autoChargePosted");
    }

    void postInsurancePayment(UUID patientId, UUID claimId, String carrierName,
                              BigDecimal amount) {
        LedgerEntry entry = post(LedgerEntry.insurancePayment(
                DEFAULT_CLINIC_ID, patientId, amount,
                "Insurance payment — " + carrierName, claimId,
                CurrentUser.id().orElse(null)));
        audit(entry, "insurancePaymentPosted");
    }

    boolean hasChargesForAppointment(UUID appointmentId) {
        return ledger.existsByAppointmentIdAndType(appointmentId, LedgerEntry.Type.CHARGE);
    }

    // ---- helpers ----

    private void requirePatient(UUID patientId) {
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }
    }

    private void audit(LedgerEntry entry, String action) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, entry.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of(action, entry.getAmount().toString(),
                        "patientId", entry.getPatientId().toString(),
                        "type", entry.getType().name())));
    }

    private LedgerEntryResponse toResponse(LedgerEntry entry, ProcedureSummary procedure,
                                           boolean reversed) {
        return new LedgerEntryResponse(
                entry.getId(), entry.getType().name(), entry.getAmount(),
                entry.getDescription(),
                entry.getMethod() == null ? null : entry.getMethod().name(),
                entry.getProcedureCodeId(),
                procedure != null ? procedure.code() : null,
                entry.getAppointmentId(), entry.getClaimId(), entry.getEntryDate(),
                entry.getReversalOf(), reversed, entry.getCreatedAt());
    }
}
