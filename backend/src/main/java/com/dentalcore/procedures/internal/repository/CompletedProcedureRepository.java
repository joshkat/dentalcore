package com.dentalcore.procedures.internal.repository;

import com.dentalcore.procedures.internal.entity.CompletedProcedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CompletedProcedureRepository extends JpaRepository<CompletedProcedure, UUID> {

    boolean existsByAppointmentIdAndProcedureCodeId(UUID appointmentId, UUID procedureCodeId);

    boolean existsByPlannedProcedureId(UUID plannedProcedureId);

    List<CompletedProcedure> findByAppointmentIdOrderByCompletedAtAsc(UUID appointmentId);

    List<CompletedProcedure> findByPatientIdOrderByCompletedAtDesc(UUID patientId);

    List<CompletedProcedure> findByPatientIdAndEntryDateBetweenOrderByCompletedAtDesc(
            UUID patientId, LocalDate from, LocalDate to);

    List<CompletedProcedure> findByPatientIdAndEntryDateGreaterThanEqualOrderByCompletedAtDesc(
            UUID patientId, LocalDate from);

    List<CompletedProcedure> findByPatientIdAndEntryDateLessThanEqualOrderByCompletedAtDesc(
            UUID patientId, LocalDate to);
}
