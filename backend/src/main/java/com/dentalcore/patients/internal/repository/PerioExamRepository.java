package com.dentalcore.patients.internal.repository;

import com.dentalcore.patients.internal.entity.PerioExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PerioExamRepository extends JpaRepository<PerioExam, UUID> {

    List<PerioExam> findByPatientIdOrderByExamDateDescCreatedAtDesc(UUID patientId);
}
