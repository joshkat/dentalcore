package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.ScheduleBlockout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScheduleBlockoutRepository extends JpaRepository<ScheduleBlockout, UUID> {

    @Query("""
            SELECT b FROM ScheduleBlockout b
            WHERE b.startsAt < :to AND b.endsAt > :from
            ORDER BY b.startsAt
            """)
    List<ScheduleBlockout> findInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT b FROM ScheduleBlockout b
            WHERE b.operatoryId = :operatoryId
              AND b.startsAt < :endsAt AND b.endsAt > :startsAt
            """)
    List<ScheduleBlockout> findOverlapping(@Param("operatoryId") UUID operatoryId,
                                           @Param("startsAt") Instant startsAt,
                                           @Param("endsAt") Instant endsAt);
}
