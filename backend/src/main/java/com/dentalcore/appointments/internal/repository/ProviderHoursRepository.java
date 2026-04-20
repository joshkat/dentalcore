package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.ProviderHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProviderHoursRepository extends JpaRepository<ProviderHours, UUID> {

    List<ProviderHours> findByProviderIdOrderByDayOfWeekAscStartTimeAsc(UUID providerId);

    void deleteByProviderId(UUID providerId);
}
