package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.AppointmentProcedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentProcedureRepository extends JpaRepository<AppointmentProcedure, UUID> {

    List<AppointmentProcedure> findByAppointmentId(UUID appointmentId);

    List<AppointmentProcedure> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    void deleteByAppointmentId(UUID appointmentId);
}
