package com.dentalcore.insurance.internal.repository;

import com.dentalcore.insurance.internal.entity.FeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, UUID> {

    List<FeeSchedule> findAllByOrderByName();
}
