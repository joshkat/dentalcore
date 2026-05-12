package com.dentalcore.billing.internal.repository;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByPatientIdOrderByEntryDateDescCreatedAtDesc(
            UUID patientId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.patientId = :patientId")
    BigDecimal balanceFor(@Param("patientId") UUID patientId);

    boolean existsByReversalOf(UUID originalId);

    List<LedgerEntry> findByPatientIdAndEntryDateAndTypeOrderByCreatedAtAsc(
            UUID patientId, LocalDate entryDate, LedgerEntry.Type type);

    Page<LedgerEntry> findByPatientIdInOrderByEntryDateDescCreatedAtDesc(
            java.util.Collection<UUID> patientIds, Pageable pageable);

    List<LedgerEntry> findByPatientIdInAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            java.util.Collection<UUID> patientIds, LocalDate from, LocalDate to);

    /** A patient's entries of one type on/after a date, as a positive collected amount. */
    @Query("""
            SELECT COALESCE(-SUM(e.amount), 0) FROM LedgerEntry e
            WHERE e.patientId = :patientId
              AND e.type = :type
              AND e.entryDate >= :since
            """)
    BigDecimal receivedSince(@Param("patientId") UUID patientId,
                             @Param("type") LedgerEntry.Type type,
                             @Param("since") LocalDate since);
}
