package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient_insurance")
@SQLDelete(sql = "UPDATE patient_insurance SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PatientInsurance extends BaseEntity {

    public enum Relationship {
        SELF, SPOUSE, CHILD, OTHER
    }

    public enum Priority {
        PRIMARY, SECONDARY
    }

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "subscriber_patient_id", nullable = false)
    private UUID subscriberPatientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_to_subscriber", nullable = false, length = 20)
    private Relationship relationshipToSubscriber = Relationship.SELF;

    @Column(name = "member_id", nullable = false, length = 50)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority = Priority.PRIMARY;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected PatientInsurance() {
    }

    public PatientInsurance(UUID patientId, UUID planId, UUID subscriberPatientId,
                            Relationship relationshipToSubscriber, String memberId,
                            Priority priority) {
        this.patientId = patientId;
        this.planId = planId;
        this.subscriberPatientId = subscriberPatientId;
        this.relationshipToSubscriber = relationshipToSubscriber;
        this.memberId = memberId;
        this.priority = priority;
    }

    public void update(UUID planId, UUID subscriberPatientId, Relationship relationship,
                       String memberId, Priority priority, LocalDate effectiveDate,
                       LocalDate terminationDate) {
        this.planId = planId;
        this.subscriberPatientId = subscriberPatientId;
        this.relationshipToSubscriber = relationship;
        this.memberId = memberId;
        this.priority = priority;
        this.effectiveDate = effectiveDate;
        this.terminationDate = terminationDate;
    }

    public void setDates(LocalDate effectiveDate, LocalDate terminationDate) {
        this.effectiveDate = effectiveDate;
        this.terminationDate = terminationDate;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getSubscriberPatientId() {
        return subscriberPatientId;
    }

    public Relationship getRelationshipToSubscriber() {
        return relationshipToSubscriber;
    }

    public String getMemberId() {
        return memberId;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }
}
