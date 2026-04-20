package com.dentalcore.appointments.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import com.dentalcore.shared.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@SQLRestriction("deleted_at IS NULL")
public class Appointment extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "operatory_id", nullable = false)
    private UUID operatoryId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "color_override", length = 7)
    private String colorOverride;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Appointment() {
    }

    public Appointment(UUID clinicId, UUID patientId, UUID providerId, UUID operatoryId,
                       Instant startsAt, Instant endsAt) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.providerId = providerId;
        this.operatoryId = operatoryId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void transitionTo(AppointmentStatus target, String cancelReason) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidRequestException(
                    "Cannot change appointment from %s to %s".formatted(status, target));
        }
        this.status = target;
        if (target == AppointmentStatus.CANCELLED) {
            this.cancelledReason = cancelReason;
        }
    }

    public void reschedule(UUID providerId, UUID operatoryId, Instant startsAt, Instant endsAt) {
        // COMPLETED records what happened and NO_SHOW is locked by policy;
        // a CANCELLED appointment may be rebooked, returning it to SCHEDULED.
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.NO_SHOW) {
            throw new InvalidRequestException(
                    "A %s appointment cannot be modified".formatted(status));
        }
        if (status == AppointmentStatus.CANCELLED) {
            this.status = AppointmentStatus.SCHEDULED;
            this.cancelledReason = null;
        }
        this.providerId = providerId;
        this.operatoryId = operatoryId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void updateDetails(String notes, String colorOverride) {
        this.notes = notes;
        this.colorOverride = colorOverride;
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

    public UUID getOperatoryId() {
        return operatoryId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getColorOverride() {
        return colorOverride;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }
}
