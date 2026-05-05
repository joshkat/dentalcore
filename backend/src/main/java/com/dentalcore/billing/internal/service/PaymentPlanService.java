package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.dto.BillingDtos.InstallmentResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentPlanRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentPlanResponse;
import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.entity.PaymentPlan;
import com.dentalcore.billing.internal.entity.PaymentPlanInstallment;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
import com.dentalcore.billing.internal.repository.PaymentPlanRepository;
import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment plans: a fixed schedule generated at creation (down payment plus
 * equal installments until the plan total; the last installment may be
 * smaller). Progress tracking is intentionally simple — receivedToDate is the
 * sum of the patient's ledger PAYMENT entries dated on/after the plan's
 * creation date. Payments are not earmarked per plan, so any patient payment
 * made while the plan is open counts toward it.
 */
@Service
@Transactional
public class PaymentPlanService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** Sanity cap so a tiny installment cannot generate an absurd schedule. */
    private static final int MAX_INSTALLMENTS = 120;

    private final PaymentPlanRepository plans;
    private final LedgerEntryRepository ledger;
    private final PatientApi patientApi;
    private final ClinicTimeService clinicTime;
    private final ApplicationEventPublisher events;

    public PaymentPlanService(PaymentPlanRepository plans,
                              LedgerEntryRepository ledger,
                              PatientApi patientApi,
                              ClinicTimeService clinicTime,
                              ApplicationEventPublisher events) {
        this.plans = plans;
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.clinicTime = clinicTime;
        this.events = events;
    }

    public PaymentPlanResponse create(PaymentPlanRequest request) {
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        BigDecimal downPayment = request.downPayment() == null
                ? BigDecimal.ZERO : request.downPayment();
        BigDecimal remaining = request.totalAmount().subtract(downPayment);
        if (remaining.signum() <= 0) {
            throw new InvalidRequestException(
                    "The down payment must be smaller than the total amount");
        }

        BigDecimal[] split = remaining.divideAndRemainder(request.installmentAmount());
        if (split[0].compareTo(BigDecimal.valueOf(MAX_INSTALLMENTS)) > 0) {
            throw new InvalidRequestException(
                    "The schedule would exceed " + MAX_INSTALLMENTS
                            + " installments — raise the installment amount");
        }
        int fullInstallments = split[0].intValueExact();
        BigDecimal remainder = split[1];
        int count = fullInstallments + (remainder.signum() > 0 ? 1 : 0);

        PaymentPlan plan = new PaymentPlan(
                DEFAULT_CLINIC_ID, request.patientId(), request.totalAmount(),
                downPayment, request.installmentAmount(),
                PaymentPlan.Frequency.valueOf(request.frequency()),
                request.firstDueDate(), request.notes());
        for (int i = 0; i < count; i++) {
            BigDecimal amount = i < fullInstallments
                    ? request.installmentAmount() : remainder;
            plan.addInstallment(dueDate(request.firstDueDate(), plan.getFrequency(), i), amount);
        }
        plan = plans.save(plan);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "PaymentPlan", plan.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("patientId", plan.getPatientId().toString(),
                        "totalAmount", plan.getTotalAmount().toString(),
                        "installments", String.valueOf(count))));
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<PaymentPlanResponse> listFor(UUID patientId) {
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }
        return plans.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PaymentPlanResponse updateStatus(UUID id, String status) {
        PaymentPlan plan = plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment plan", id));
        if (plan.getStatus() != PaymentPlan.Status.ACTIVE) {
            throw new InvalidRequestException(
                    "Only an ACTIVE plan can change status (this plan is "
                            + plan.getStatus() + ")");
        }
        PaymentPlan.Status next = PaymentPlan.Status.valueOf(status);
        plan.setStatus(next);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), "PaymentPlan", plan.getId(),
                AuditEvent.AuditAction.STATUS_CHANGE,
                Map.of("status", "ACTIVE"),
                Map.of("status", next.name())));
        return toResponse(plan);
    }

    private static LocalDate dueDate(LocalDate first, PaymentPlan.Frequency frequency, int index) {
        return frequency == PaymentPlan.Frequency.MONTHLY
                ? first.plusMonths(index)
                : first.plusWeeks(2L * index);
    }

    private PaymentPlanResponse toResponse(PaymentPlan plan) {
        LocalDate today = clinicTime.today(plan.getClinicId());
        BigDecimal expectedToDate = plan.getDownPayment().add(
                plan.getInstallments().stream()
                        .filter(i -> !i.getDueDate().isAfter(today))
                        .map(PaymentPlanInstallment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        LocalDate planStart = plan.getCreatedAt() == null
                ? today
                : LocalDate.ofInstant(plan.getCreatedAt(),
                        clinicTime.clinicZone(plan.getClinicId()));
        BigDecimal receivedToDate = ledger.receivedSince(
                plan.getPatientId(), LedgerEntry.Type.PAYMENT, planStart);

        return new PaymentPlanResponse(
                plan.getId(), plan.getPatientId(), plan.getTotalAmount(),
                plan.getDownPayment(), plan.getInstallmentAmount(),
                plan.getFrequency().name(), plan.getFirstDueDate(),
                plan.getStatus().name(), plan.getNotes(),
                plan.getInstallments().stream()
                        .map(i -> new InstallmentResponse(i.getDueDate(), i.getAmount()))
                        .toList(),
                expectedToDate, receivedToDate, plan.getCreatedAt());
    }
}
