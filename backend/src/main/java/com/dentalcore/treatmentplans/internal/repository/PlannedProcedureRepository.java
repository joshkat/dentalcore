package com.dentalcore.treatmentplans.internal.repository;

import com.dentalcore.treatmentplans.internal.entity.PlannedProcedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlannedProcedureRepository extends JpaRepository<PlannedProcedure, UUID> {

    @Query("""
            SELECT p FROM PlannedProcedure p JOIN FETCH p.treatmentPlan tp
            WHERE tp.patientId = :patientId
              AND p.tooth IS NOT NULL
              AND tp.status <> com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus.CANCELLED
            ORDER BY p.createdAt
            """)
    List<PlannedProcedure> findToothProceduresForPatient(@Param("patientId") UUID patientId);
}
