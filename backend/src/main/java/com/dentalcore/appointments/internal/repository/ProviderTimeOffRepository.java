package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.ProviderTimeOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProviderTimeOffRepository extends JpaRepository<ProviderTimeOff, UUID> {

    List<ProviderTimeOff> findByProviderIdOrderByStartsAtDesc(UUID providerId);

    @Query("""
            SELECT t FROM ProviderTimeOff t
            WHERE t.providerId = :providerId
              AND t.startsAt < :endsAt AND t.endsAt > :startsAt
            """)
    List<ProviderTimeOff> findOverlapping(@Param("providerId") UUID providerId,
                                          @Param("startsAt") Instant startsAt,
                                          @Param("endsAt") Instant endsAt);
}
