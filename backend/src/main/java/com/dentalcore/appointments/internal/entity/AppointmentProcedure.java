package com.dentalcore.appointments.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "appointment_procedures")
public class AppointmentProcedure extends BaseEntity {

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "procedure_code_id", nullable = false)
    private UUID procedureCodeId;

    protected AppointmentProcedure() {
    }

    public AppointmentProcedure(UUID appointmentId, UUID procedureCodeId) {
        this.appointmentId = appointmentId;
        this.procedureCodeId = procedureCodeId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }
}
