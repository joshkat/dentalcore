package com.dentalcore.billing.internal.repository;

import com.dentalcore.billing.internal.entity.StatementRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StatementRunRepository extends JpaRepository<StatementRun, UUID> {

    Page<StatementRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
