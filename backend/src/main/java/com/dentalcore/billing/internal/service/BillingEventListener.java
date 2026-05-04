package com.dentalcore.billing.internal.service;

import com.dentalcore.insurance.api.ClaimPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts ledger entries from domain events. Runs in the publishing transaction
 * so payments commit atomically with the claim payment.
 *
 * <p>Appointment-completion charges no longer post from here: the procedures
 * module records a completed procedure per code (skipping codes already
 * completed during checkout) and posts each charge through
 * {@link com.dentalcore.billing.api.BillingPostingApi}, so a visit is never
 * double-charged.
 */
@Component
public class BillingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BillingEventListener.class);

    private final BillingService billingService;

    public BillingEventListener(BillingService billingService) {
        this.billingService = billingService;
    }

    @EventListener
    @Transactional
    public void onClaimPaid(ClaimPaidEvent event) {
        billingService.postInsurancePayment(
                event.patientId(), event.claimId(), event.carrierName(), event.totalPaid());
        log.info("Posted insurance payment {} for claim {}", event.totalPaid(), event.claimId());
    }
}
