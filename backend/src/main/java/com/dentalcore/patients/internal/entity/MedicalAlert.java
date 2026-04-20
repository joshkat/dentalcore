package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "medical_alerts")
public class MedicalAlert extends BaseEntity {

    public enum AlertType {
        ALLERGY, CONDITION, ALERT, MEDICATION
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AlertType type;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity = Severity.MEDIUM;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected MedicalAlert() {
    }

    public MedicalAlert(UUID patientId, AlertType type, String description, Severity severity) {
        this.patientId = patientId;
        this.type = type;
        this.description = description;
        this.severity = severity;
    }

    public void update(AlertType type, String description, Severity severity, boolean active) {
        this.type = type;
        this.description = description;
        this.severity = severity;
        this.active = active;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public AlertType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isActive() {
        return active;
    }
}
