package com.dentalcore.documents.internal.repository;

import com.dentalcore.documents.internal.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);
}
