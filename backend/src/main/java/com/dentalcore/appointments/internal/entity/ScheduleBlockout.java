package com.dentalcore.appointments.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A span during which an operatory (chair/room) cannot be booked. */
@Entity
@Table(name = "schedule_blockouts")
public class ScheduleBlockout extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "operatory_id", nullable = false)
    private UUID operatoryId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reason", length = 200)
    private String reason;

    protected ScheduleBlockout() {
    }

    public ScheduleBlockout(UUID clinicId, UUID operatoryId, Instant startsAt, Instant endsAt,
                            String reason) {
        this.clinicId = clinicId;
        this.operatoryId = operatoryId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public UUID getClinicId() {
        return clinicId;
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

    public String getReason() {
        return reason;
    }
}
