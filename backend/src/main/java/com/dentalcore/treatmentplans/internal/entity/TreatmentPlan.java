package com.dentalcore.treatmentplans.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "treatment_plans")
@SQLDelete(sql = "UPDATE treatment_plans SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class TreatmentPlan extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreatmentPlanStatus status = TreatmentPlanStatus.DRAFT;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "treatmentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC, createdAt ASC")
    private List<PlannedProcedure> procedures = new ArrayList<>();

    protected TreatmentPlan() {
    }

    public TreatmentPlan(UUID clinicId, UUID patientId, UUID providerId, String title) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.providerId = providerId;
        this.title = title;
    }

    public void transitionTo(TreatmentPlanStatus target, UUID actingUserId) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidRequestException(
                    "Cannot change treatment plan from %s to %s".formatted(status, target));
        }
        if (target == TreatmentPlanStatus.APPROVED) {
            this.approvedAt = Instant.now();
            this.approvedBy = actingUserId;
        }
        this.status = target;
    }

    public void rename(String title, String notes) {
        if (status.isTerminal()) {
            throw new InvalidRequestException(
                    "A %s treatment plan cannot be modified".formatted(status));
        }
        this.title = title;
        this.notes = notes;
    }

    public PlannedProcedure addProcedure(PlannedProcedure procedure) {
        if (!status.allowsProcedureEdits()) {
            throw new InvalidRequestException(
                    "Procedures can only be added while the plan is DRAFT or PRESENTED (current: %s)"
                            .formatted(status));
        }
        procedure.attachTo(this);
        procedures.add(procedure);
        return procedure;
    }

    public void removeProcedure(PlannedProcedure procedure) {
        if (!status.allowsProcedureEdits()) {
            throw new InvalidRequestException(
                    "Procedures can only be removed while the plan is DRAFT or PRESENTED (current: %s)"
                            .formatted(status));
        }
        procedures.remove(procedure);
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

    public String getTitle() {
        return title;
    }

    public TreatmentPlanStatus getStatus() {
        return status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public String getNotes() {
        return notes;
    }

    public List<PlannedProcedure> getProcedures() {
        return procedures;
    }
}
