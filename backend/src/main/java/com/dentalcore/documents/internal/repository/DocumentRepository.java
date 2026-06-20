package com.dentalcore.documents.internal.repository;

import com.dentalcore.documents.internal.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    /**
     * Storage keys that must be kept: live documents plus those soft-deleted
     * within the recovery grace window. Native SQL deliberately bypasses the
     * entity's {@code @SQLRestriction("deleted_at IS NULL")} so soft-deleted
     * rows remain visible to the retention sweep.
     */
    @Query(value = "SELECT storage_key FROM documents "
            + "WHERE deleted_at IS NULL OR deleted_at >= :cutoff", nativeQuery = true)
    Set<String> findRetainableStorageKeys(@Param("cutoff") Instant cutoff);
}
