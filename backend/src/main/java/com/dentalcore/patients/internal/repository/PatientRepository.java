package com.dentalcore.patients.internal.repository;

import com.dentalcore.patients.internal.entity.Patient;
import com.dentalcore.patients.internal.entity.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    @Query("""
            SELECT DISTINCT p FROM Patient p LEFT JOIN p.phones ph
            WHERE lower(p.firstName) LIKE lower(concat('%', :q, '%'))
               OR lower(p.lastName) LIKE lower(concat('%', :q, '%'))
               OR lower(concat(p.lastName, ', ', p.firstName)) LIKE lower(concat('%', :q, '%'))
               OR lower(coalesce(p.email, '')) LIKE lower(concat('%', :q, '%'))
               OR ph.number LIKE concat('%', :q, '%')
            """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);

    Page<Patient> findByStatus(PatientStatus status, Pageable pageable);
}
