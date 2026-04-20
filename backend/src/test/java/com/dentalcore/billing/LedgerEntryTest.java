package com.dentalcore.billing;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTest {

    private final UUID clinicId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    @Test
    void chargesAreAlwaysPositive() {
        LedgerEntry charge = LedgerEntry.charge(clinicId, patientId,
                new BigDecimal("-120.00"), "D1110", null, null, null);
        assertThat(charge.getAmount()).isEqualByComparingTo("120.00");
        assertThat(charge.getType()).isEqualTo(LedgerEntry.Type.CHARGE);
    }

    @Test
    void paymentsAreAlwaysNegative() {
        LedgerEntry payment = LedgerEntry.payment(clinicId, patientId,
                new BigDecimal("96.00"), LedgerEntry.Method.CARD, "Card payment", null);
        assertThat(payment.getAmount()).isEqualByComparingTo("-96.00");
        assertThat(payment.getMethod()).isEqualTo(LedgerEntry.Method.CARD);
    }

    @Test
    void insurancePaymentsAreAlwaysNegative() {
        LedgerEntry payment = LedgerEntry.insurancePayment(clinicId, patientId,
                new BigDecimal("80.00"), "Delta", UUID.randomUUID(), null);
        assertThat(payment.getAmount()).isEqualByComparingTo("-80.00");
        assertThat(payment.getType()).isEqualTo(LedgerEntry.Type.INSURANCE_PAYMENT);
    }

    @Test
    void adjustmentsKeepTheirSign() {
        assertThat(LedgerEntry.adjustment(clinicId, patientId,
                new BigDecimal("-25.00"), "Courtesy discount", null).getAmount())
                .isEqualByComparingTo("-25.00");
        assertThat(LedgerEntry.adjustment(clinicId, patientId,
                new BigDecimal("10.00"), "Late fee", null).getAmount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void reversalNegatesTheOriginalAndLinksIt() {
        LedgerEntry charge = LedgerEntry.charge(clinicId, patientId,
                new BigDecimal("120.00"), "D1110", null, null, null);
        LedgerEntry reversal = LedgerEntry.reversalOf(charge, "posted in error", null);

        assertThat(reversal.getAmount()).isEqualByComparingTo("-120.00");
        assertThat(reversal.getType()).isEqualTo(LedgerEntry.Type.ADJUSTMENT);
        assertThat(reversal.getDescription()).contains("posted in error");
        assertThat(reversal.getPatientId()).isEqualTo(patientId);
    }
}
