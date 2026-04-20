package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.PatientInsurance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientInsuranceRepository extends JpaRepository<PatientInsurance, UUID> {

    List<PatientInsurance> findByPatientIdOrderByPriorityAsc(UUID patientId);
}
