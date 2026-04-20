package com.dentalcore.billing.internal.service;

import com.dentalcore.appointments.api.AppointmentCompletedEvent;
import com.dentalcore.insurance.api.ClaimPaidEvent;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Posts ledger entries from domain events. Runs in the publishing transaction
 * so charges commit atomically with the appointment completion / claim payment.
 */
@Component
public class BillingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BillingEventListener.class);

    private final BillingService billingService;
    private final ProcedureCatalogApi catalogApi;

    public BillingEventListener(BillingService billingService, ProcedureCatalogApi catalogApi) {
        this.billingService = billingService;
        this.catalogApi = catalogApi;
    }

    @EventListener
    @Transactional
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        if (event.procedureCodeIds().isEmpty()) {
            return;
        }
        // Idempotency guard: completion is a one-way transition, but never double-post.
        if (billingService.hasChargesForAppointment(event.appointmentId())) {
            log.warn("Charges already posted for appointment {}", event.appointmentId());
            return;
        }
        Map<UUID, ProcedureSummary> catalog =
                catalogApi.findSummaries(Set.copyOf(event.procedureCodeIds()));
        for (UUID codeId : event.procedureCodeIds()) {
            ProcedureSummary procedure = catalog.get(codeId);
            if (procedure != null) {
                billingService.postAutoCharge(event.patientId(), event.appointmentId(), procedure);
            }
        }
        log.info("Posted {} charge(s) for completed appointment {}",
                event.procedureCodeIds().size(), event.appointmentId());
    }

    @EventListener
    @Transactional
    public void onClaimPaid(ClaimPaidEvent event) {
        billingService.postInsurancePayment(
                event.patientId(), event.claimId(), event.carrierName(), event.totalPaid());
        log.info("Posted insurance payment {} for claim {}", event.totalPaid(), event.claimId());
    }
}
