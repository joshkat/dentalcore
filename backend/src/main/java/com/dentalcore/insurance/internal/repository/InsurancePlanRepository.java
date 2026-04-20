package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.InsurancePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, UUID> {

    List<InsurancePlan> findByCarrierIdOrderByPlanName(UUID carrierId);
}
