package com.dentalcore.billing.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Append-only: entries are never updated or deleted. Corrections are
 * reversing ADJUSTMENT entries pointing at the original via reversalOf.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    public enum Type {
        CHARGE, PAYMENT, ADJUSTMENT, INSURANCE_PAYMENT
    }

    public enum Method {
        CASH, CARD, CHECK, OTHER
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10)
    private Method method;

    @Column(name = "procedure_code_id")
    private UUID procedureCodeId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate = LocalDate.now();

    @Column(name = "reversal_of", unique = true)
    private UUID reversalOf;

    @Column(name = "created_by")
    private UUID createdBy;

    protected LedgerEntry() {
    }

    private LedgerEntry(UUID clinicId, UUID patientId, Type type, BigDecimal amount,
                        String description, UUID createdBy) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.createdBy = createdBy;
    }

    public static LedgerEntry charge(UUID clinicId, UUID patientId, BigDecimal amount,
                                     String description, UUID procedureCodeId,
                                     UUID appointmentId, UUID createdBy) {
        LedgerEntry entry = new LedgerEntry(clinicId, patientId, Type.CHARGE,
                amount.abs(), description, createdBy);
        entry.procedureCodeId = procedureCodeId;
        entry.appointmentId = appointmentId;
        return entry;
    }

    public static LedgerEntry payment(UUID clinicId, UUID patientId, BigDecimal amount,
                                      Method method, String description, UUID createdBy) {
        LedgerEntry entry = new LedgerEntry(clinicId, patientId, Type.PAYMENT,
                amount.abs().negate(), description, createdBy);
        entry.method = method;
        return entry;
    }

    public static LedgerEntry insurancePayment(UUID clinicId, UUID patientId, BigDecimal amount,
                                               String description, UUID claimId, UUID createdBy) {
        LedgerEntry entry = new LedgerEntry(clinicId, patientId, Type.INSURANCE_PAYMENT,
                amount.abs().negate(), description, createdBy);
        entry.claimId = claimId;
        return entry;
    }

    public static LedgerEntry adjustment(UUID clinicId, UUID patientId, BigDecimal signedAmount,
                                         String description, UUID createdBy) {
        return new LedgerEntry(clinicId, patientId, Type.ADJUSTMENT,
                signedAmount, description, createdBy);
    }

    /** Negating ADJUSTMENT entry that voids the original. */
    public static LedgerEntry reversalOf(LedgerEntry original, String reason, UUID createdBy) {
        LedgerEntry entry = new LedgerEntry(original.clinicId, original.patientId,
                Type.ADJUSTMENT, original.amount.negate(),
                "Reversal: " + reason, createdBy);
        entry.reversalOf = original.getId();
        entry.procedureCodeId = original.procedureCodeId;
        entry.appointmentId = original.appointmentId;
        entry.claimId = original.claimId;
        return entry;
    }

    /** Assigns the clinic-local business date (otherwise defaults to server date). */
    public LedgerEntry at(LocalDate entryDate) {
        this.entryDate = entryDate;
        return this;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Method getMethod() {
        return method;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public UUID getReversalOf() {
        return reversalOf;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
