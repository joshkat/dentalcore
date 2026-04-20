package com.dentalcore.patients.internal.repository;

import com.dentalcore.patients.internal.entity.FamilyLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyLinkRepository extends JpaRepository<FamilyLink, UUID> {

    List<FamilyLink> findByPatientId(UUID patientId);

    Optional<FamilyLink> findByPatientIdAndRelatedPatientId(UUID patientId, UUID relatedPatientId);

    boolean existsByPatientIdAndRelatedPatientId(UUID patientId, UUID relatedPatientId);
}
