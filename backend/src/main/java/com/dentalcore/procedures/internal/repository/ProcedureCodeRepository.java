package com.dentalcore.procedures.internal.repository;

import com.dentalcore.procedures.internal.entity.ProcedureCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProcedureCodeRepository extends JpaRepository<ProcedureCode, UUID> {

    Optional<ProcedureCode> findByCodeIgnoreCase(String code);

    @Query("""
            SELECT p FROM ProcedureCode p
            WHERE lower(p.code) LIKE lower(concat('%', :q, '%'))
               OR lower(p.description) LIKE lower(concat('%', :q, '%'))
            """)
    Page<ProcedureCode> search(@Param("q") String q, Pageable pageable);

    Page<ProcedureCode> findByActiveTrue(Pageable pageable);
}
