package com.dentalcore.appointments.internal.repository;

import com.dentalcore.appointments.internal.entity.Operatory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OperatoryRepository extends JpaRepository<Operatory, UUID> {

    List<Operatory> findByActiveTrueOrderByName();

    List<Operatory> findAllByOrderByName();
}
