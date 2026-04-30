package com.dentalcore.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public posting interface of the billing module. The procedures module posts
 * one ledger charge per completed procedure through this API (replacing the
 * old appointment-completion auto-charge listener) and reverses that charge
 * when a same-day completion is undone.
 */
public interface BillingPostingApi {

    /** Posts a CHARGE ledger entry; returns the new ledger entry id. */
    UUID postProcedureCharge(UUID clinicId, UUID patientId, UUID procedureCodeId,
                             String description, BigDecimal amount, LocalDate entryDate);

    /** Same as above, additionally linking the appointment on the ledger entry. */
    UUID postProcedureCharge(UUID clinicId, UUID patientId, UUID procedureCodeId,
                             UUID appointmentId, String description, BigDecimal amount,
                             LocalDate entryDate);

    /** Voids an entry with a negating reversal; returns the reversal entry id. */
    UUID reverseEntry(UUID ledgerEntryId, String reason);
}
