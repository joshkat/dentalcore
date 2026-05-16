package com.dentalcore.forms.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "form_instances")
public class FormInstance extends BaseEntity {

    public enum Status {
        DRAFT, COMPLETED, SIGNED
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> answers = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by_name", length = 160)
    private String signedByName;

    @Column(name = "document_id")
    private UUID documentId;

    protected FormInstance() {
    }

    public FormInstance(UUID clinicId, UUID templateId, UUID patientId) {
        this.clinicId = clinicId;
        this.templateId = templateId;
        this.patientId = patientId;
    }

    /** Replaces the answers; the status follows required-field completeness. */
    public void updateAnswers(Map<String, Object> answers, boolean allRequiredAnswered) {
        if (status == Status.SIGNED) {
            throw new ConflictException("A signed form cannot be modified");
        }
        this.answers = answers;
        this.status = allRequiredAnswered ? Status.COMPLETED : Status.DRAFT;
    }

    public void sign(String signedByName, UUID documentId) {
        if (status != Status.COMPLETED) {
            throw new InvalidRequestException(
                    "Only a COMPLETED form can be signed");
        }
        this.status = Status.SIGNED;
        this.signedAt = Instant.now();
        this.signedByName = signedByName;
        this.documentId = documentId;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getSignedByName() {
        return signedByName;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
