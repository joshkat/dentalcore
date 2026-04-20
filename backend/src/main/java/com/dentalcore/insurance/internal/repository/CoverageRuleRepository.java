package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.CoverageRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoverageRuleRepository extends JpaRepository<CoverageRule, UUID> {

    List<CoverageRule> findByPlanId(UUID planId);
}
