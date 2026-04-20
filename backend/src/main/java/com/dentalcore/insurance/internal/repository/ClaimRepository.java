package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.Claim;
import com.dentalcore.insurance.internal.entity.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Page<Claim> findByStatusOrderByCreatedAtDesc(ClaimStatus status, Pageable pageable);

    Page<Claim> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    Page<Claim> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(p.paidAmount), 0) FROM Claim c JOIN c.procedures p
            WHERE c.patientInsuranceId = :coverageId
              AND c.status IN (com.dentalcore.insurance.internal.entity.ClaimStatus.PAID,
                               com.dentalcore.insurance.internal.entity.ClaimStatus.CLOSED)
              AND c.createdAt >= :since
            """)
    java.math.BigDecimal benefitsPaidSince(
            @org.springframework.data.repository.query.Param("coverageId") UUID coverageId,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
