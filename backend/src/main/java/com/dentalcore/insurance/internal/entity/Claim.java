package com.dentalcore.insurance.internal.entity;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim extends BaseEntity {

    @Column(name = "patient_insurance_id", nullable = false)
    private UUID patientInsuranceId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ClaimProcedure> procedures = new ArrayList<>();

    protected Claim() {
    }

    public Claim(UUID patientInsuranceId, UUID patientId, String notes) {
        this.patientInsuranceId = patientInsuranceId;
        this.patientId = patientId;
        this.notes = notes;
    }

    public void transitionTo(ClaimStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidRequestException(
                    "Cannot change claim from %s to %s".formatted(status, target));
        }
        if (target == ClaimStatus.SUBMITTED) {
            if (procedures.isEmpty()) {
                throw new InvalidRequestException("A claim needs at least one line item");
            }
            this.submittedAt = Instant.now();
        }
        this.status = target;
    }

    public ClaimProcedure addProcedure(ClaimProcedure procedure) {
        if (!status.allowsLineItemEdits()) {
            throw new InvalidRequestException(
                    "Line items can only change while the claim is DRAFT (current: %s)"
                            .formatted(status));
        }
        procedure.attachTo(this);
        procedures.add(procedure);
        return procedure;
    }

    public void removeProcedure(ClaimProcedure procedure) {
        if (!status.allowsLineItemEdits()) {
            throw new InvalidRequestException(
                    "Line items can only change while the claim is DRAFT (current: %s)"
                            .formatted(status));
        }
        procedures.remove(procedure);
    }

    public BigDecimal totalBilled() {
        return procedures.stream().map(ClaimProcedure::getBilledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalPaid() {
        return procedures.stream().map(ClaimProcedure::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getPatientInsuranceId() {
        return patientInsuranceId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<ClaimProcedure> getProcedures() {
        return procedures;
    }
}
