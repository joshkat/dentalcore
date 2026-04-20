package com.dentalcore.providers.internal.repository;

import com.dentalcore.providers.internal.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    Optional<Provider> findByNpi(String npi);

    Page<Provider> findByActiveTrue(Pageable pageable);
}
