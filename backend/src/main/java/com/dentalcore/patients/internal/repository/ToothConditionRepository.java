package com.dentalcore.patients.internal.repository;

import com.dentalcore.patients.internal.entity.ToothCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToothConditionRepository extends JpaRepository<ToothCondition, UUID> {

    List<ToothCondition> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<ToothCondition> findByPatientIdAndToothAndStatus(
            UUID patientId, String tooth, ToothCondition.Status status);
}
