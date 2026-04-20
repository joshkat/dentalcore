package com.dentalcore.insurance.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a claim transitions to PAID. Billing posts the carrier's
 * payment to the patient ledger from this.
 */
public record ClaimPaidEvent(
        UUID claimId,
        UUID patientId,
        String carrierName,
        BigDecimal totalPaid
) {
}
