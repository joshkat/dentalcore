package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /**
     * Calendar-blocking appointments that overlap [startsAt, endsAt) for the given
     * provider or operatory. Used for friendly conflict errors; the DB exclusion
     * constraint remains the correctness backstop under concurrency.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.startsAt < :endsAt AND a.endsAt > :startsAt
              AND a.status NOT IN (com.dentalcore.appointments.internal.entity.AppointmentStatus.CANCELLED,
                                   com.dentalcore.appointments.internal.entity.AppointmentStatus.NO_SHOW)
              AND (a.providerId = :providerId OR a.operatoryId = :operatoryId)
            """)
    List<Appointment> findOverlapping(@Param("providerId") UUID providerId,
                                      @Param("operatoryId") UUID operatoryId,
                                      @Param("startsAt") Instant startsAt,
                                      @Param("endsAt") Instant endsAt);
}
