package com.dentalcore.reminders.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminders")
public class Reminder extends BaseEntity {

    public enum Type {
        APPOINTMENT, RECALL
    }

    public enum Channel {
        EMAIL, SMS, NONE
    }

    public enum Status {
        SENT, FAILED, SKIPPED
    }

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 15)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected Reminder() {
    }

    public Reminder(UUID patientId, UUID appointmentId, Type type, Channel channel,
                    Status status, String detail) {
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.type = type;
        this.channel = channel;
        this.status = status;
        this.detail = detail;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public Type getType() {
        return type;
    }

    public Channel getChannel() {
        return channel;
    }

    public Status getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
