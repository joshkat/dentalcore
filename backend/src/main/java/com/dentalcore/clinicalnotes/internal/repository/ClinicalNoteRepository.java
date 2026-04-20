package com.dentalcore.clinicalnotes.internal.repository;

import com.dentalcore.clinicalnotes.internal.entity.ClinicalNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    Page<ClinicalNote> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
