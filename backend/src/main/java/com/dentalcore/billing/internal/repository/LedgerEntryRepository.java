package com.dentalcore.billing.internal.repository;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByPatientIdOrderByEntryDateDescCreatedAtDesc(
            UUID patientId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.patientId = :patientId")
    BigDecimal balanceFor(@Param("patientId") UUID patientId);

    boolean existsByReversalOf(UUID originalId);

    boolean existsByAppointmentIdAndType(UUID appointmentId, LedgerEntry.Type type);
}
