package com.dentalcore.forms.internal.repository;

import com.dentalcore.forms.internal.entity.FormInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FormInstanceRepository extends JpaRepository<FormInstance, UUID> {

    List<FormInstance> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    boolean existsByTemplateId(UUID templateId);
}
