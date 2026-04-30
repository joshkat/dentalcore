package com.dentalcore.procedures.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A procedure actually performed on a patient. Each row may carry the ledger
 * charge it produced (ledgerEntryId) and the planned procedure it fulfils.
 * Undo is same-day only: the charge is reversed and the row deleted.
 */
@Entity
@Table(name = "completed_procedures")
public class CompletedProcedure extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "procedure_code_id", nullable = false)
    private UUID procedureCodeId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "planned_procedure_id")
    private UUID plannedProcedureId;

    @Column(name = "tooth", length = 4)
    private String tooth;

    @Column(name = "surfaces", length = 16)
    private String surfaces;

    @Column(name = "fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    protected CompletedProcedure() {
    }

    public CompletedProcedure(UUID clinicId, UUID patientId, UUID providerId,
                              UUID procedureCodeId, UUID appointmentId, UUID plannedProcedureId,
                              String tooth, String surfaces, BigDecimal fee, String notes,
                              Instant completedAt, LocalDate entryDate) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.providerId = providerId;
        this.procedureCodeId = procedureCodeId;
        this.appointmentId = appointmentId;
        this.plannedProcedureId = plannedProcedureId;
        this.tooth = tooth;
        this.surfaces = surfaces;
        this.fee = fee;
        this.notes = notes;
        this.completedAt = completedAt;
        this.entryDate = entryDate;
    }

    public void linkLedgerEntry(UUID ledgerEntryId) {
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPlannedProcedureId() {
        return plannedProcedureId;
    }

    public String getTooth() {
        return tooth;
    }

    public String getSurfaces() {
        return surfaces;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }
}
