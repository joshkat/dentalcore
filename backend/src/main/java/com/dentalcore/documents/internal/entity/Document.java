package com.dentalcore.documents.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
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
@Table(name = "documents")
@SQLDelete(sql = "UPDATE documents SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Document extends BaseEntity {

    public enum Category {
        XRAY, PHOTO, CONSENT, INSURANCE, REFERRAL, OTHER
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category = Category.OTHER;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Document() {
    }

    public Document(UUID clinicId, UUID patientId, Category category, String filename,
                    String contentType, long sizeBytes, String storageKey, UUID uploadedBy,
                    String notes) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.category = category;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.notes = notes;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Category getCategory() {
        return category;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public String getNotes() {
        return notes;
    }
}
