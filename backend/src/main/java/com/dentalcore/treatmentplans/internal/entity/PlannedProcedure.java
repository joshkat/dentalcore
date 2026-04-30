package com.dentalcore.treatmentplans.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planned_procedures")
public class PlannedProcedure extends BaseEntity {

    public enum Status {
        PLANNED, SCHEDULED, COMPLETED, CANCELLED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false)
    private TreatmentPlan treatmentPlan;

    @Column(name = "procedure_code_id", nullable = false)
    private UUID procedureCodeId;

    @Column(name = "tooth", length = 5)
    private String tooth;

    @Column(name = "surface", length = 10)
    private String surface;

    @Column(name = "priority", nullable = false)
    private int priority = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PLANNED;

    @Column(name = "estimated_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PlannedProcedure() {
    }

    public PlannedProcedure(UUID procedureCodeId, String tooth, String surface,
                            int priority, BigDecimal estimatedCost) {
        this.procedureCodeId = procedureCodeId;
        this.tooth = tooth;
        this.surface = surface;
        this.priority = priority;
        this.estimatedCost = estimatedCost;
    }

    void attachTo(TreatmentPlan plan) {
        this.treatmentPlan = plan;
    }

    public void updateDetails(String tooth, String surface, int priority,
                              BigDecimal estimatedCost) {
        if (status == Status.COMPLETED || status == Status.CANCELLED) {
            throw new InvalidRequestException(
                    "A %s procedure cannot be modified".formatted(status));
        }
        this.tooth = tooth;
        this.surface = surface;
        this.priority = priority;
        this.estimatedCost = estimatedCost;
    }

    public void changeStatus(Status target) {
        boolean allowed = switch (status) {
            case PLANNED -> target == Status.SCHEDULED || target == Status.COMPLETED
                    || target == Status.CANCELLED;
            case SCHEDULED -> target == Status.COMPLETED || target == Status.CANCELLED
                    || target == Status.PLANNED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidRequestException(
                    "Cannot change procedure from %s to %s".formatted(status, target));
        }
        this.status = target;
        this.completedAt = target == Status.COMPLETED ? Instant.now() : null;
    }

    /**
     * Undo path for a reversed completed procedure: COMPLETED is terminal for
     * the normal lifecycle, so reverting deliberately bypasses
     * {@link #changeStatus}.
     */
    public void revertToPlanned() {
        if (status != Status.COMPLETED) {
            throw new InvalidRequestException(
                    "Only a COMPLETED procedure can be reverted to PLANNED");
        }
        this.status = Status.PLANNED;
        this.completedAt = null;
    }

    public TreatmentPlan getTreatmentPlan() {
        return treatmentPlan;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }

    public String getTooth() {
        return tooth;
    }

    public String getSurface() {
        return surface;
    }

    public int getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
