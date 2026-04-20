package com.dentalcore.treatmentplans.internal.repository;

import com.dentalcore.treatmentplans.internal.entity.TreatmentPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

    Page<TreatmentPlan> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
