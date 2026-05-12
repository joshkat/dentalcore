package com.dentalcore.billing.internal.repository;

import com.dentalcore.billing.internal.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    List<PaymentPlan> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
