package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.InsuranceCarrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InsuranceCarrierRepository extends JpaRepository<InsuranceCarrier, UUID> {

    @Query("""
            SELECT c FROM InsuranceCarrier c
            WHERE lower(c.name) LIKE lower(concat('%', :q, '%'))
            """)
    Page<InsuranceCarrier> search(@Param("q") String q, Pageable pageable);
}
