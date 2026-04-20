package com.dentalcore.patients.internal.repository;

import com.dentalcore.patients.internal.entity.MedicalAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalAlertRepository extends JpaRepository<MedicalAlert, UUID> {

    List<MedicalAlert> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
