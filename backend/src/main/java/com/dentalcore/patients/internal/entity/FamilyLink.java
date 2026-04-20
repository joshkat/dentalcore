package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "family_links")
public class FamilyLink extends BaseEntity {

    public enum Relationship {
        GUARANTOR, SPOUSE, CHILD, PARENT, SIBLING, OTHER
    }

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "related_patient_id", nullable = false)
    private UUID relatedPatientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 20)
    private Relationship relationship;

    protected FamilyLink() {
    }

    public FamilyLink(UUID patientId, UUID relatedPatientId, Relationship relationship) {
        this.patientId = patientId;
        this.relatedPatientId = relatedPatientId;
        this.relationship = relationship;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getRelatedPatientId() {
        return relatedPatientId;
    }

    public Relationship getRelationship() {
        return relationship;
    }
}
