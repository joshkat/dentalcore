package com.dentalcore.clinicalnotes.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_notes")
@SQLDelete(sql = "UPDATE clinical_notes SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClinicalNote extends BaseEntity {

    public enum NoteType {
        EXAM, PROGRESS, PROCEDURE, PHONE, OTHER
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType noteType = NoteType.PROGRESS;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by")
    private UUID signedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ClinicalNote() {
    }

    public ClinicalNote(UUID clinicId, UUID patientId, UUID providerId, UUID appointmentId,
                        UUID authorUserId, NoteType noteType, String body) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.providerId = providerId;
        this.appointmentId = appointmentId;
        this.authorUserId = authorUserId;
        this.noteType = noteType;
        this.body = body;
    }

    public boolean isSigned() {
        return signedAt != null;
    }

    public void edit(NoteType noteType, String body) {
        requireUnsigned();
        this.noteType = noteType;
        this.body = body;
    }

    public void sign(UUID userId) {
        requireUnsigned();
        this.signedAt = Instant.now();
        this.signedBy = userId;
    }

    public void requireUnsigned() {
        if (isSigned()) {
            throw new InvalidRequestException("A signed clinical note cannot be modified");
        }
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

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public NoteType getNoteType() {
        return noteType;
    }

    public String getBody() {
        return body;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public UUID getSignedBy() {
        return signedBy;
    }
}
