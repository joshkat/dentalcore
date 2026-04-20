package com.dentalcore.providers.internal.service;

import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.providers.api.ProviderSummary;
import com.dentalcore.providers.internal.dto.ProviderRequest;
import com.dentalcore.providers.internal.dto.ProviderResponse;
import com.dentalcore.providers.internal.entity.Provider;
import com.dentalcore.providers.internal.repository.ProviderRepository;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ProviderService implements ProviderApi {

    private static final String ENTITY_TYPE = "Provider";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ProviderRepository providerRepository;
    private final ApplicationEventPublisher events;

    public ProviderService(ProviderRepository providerRepository,
                           ApplicationEventPublisher events) {
        this.providerRepository = providerRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<ProviderResponse> list(boolean includeInactive, Pageable pageable) {
        Page<Provider> page = includeInactive
                ? providerRepository.findAll(pageable)
                : providerRepository.findByActiveTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProviderResponse get(UUID id) {
        return toResponse(findProvider(id));
    }

    public ProviderResponse create(ProviderRequest request) {
        requireUniqueNpi(request.npi(), null);
        Provider provider = new Provider(
                DEFAULT_CLINIC_ID,
                Provider.ProviderType.valueOf(request.type()),
                request.firstName(),
                request.lastName());
        apply(provider, request);
        provider = providerRepository.save(provider);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, provider.getId(),
                AuditEvent.AuditAction.CREATE,
                null,
                Map.of("name", provider.fullName(), "type", provider.getType().name())));
        return toResponse(provider);
    }

    public ProviderResponse update(UUID id, ProviderRequest request) {
        Provider provider = findProvider(id);
        requireUniqueNpi(request.npi(), id);
        Map<String, Object> before = snapshot(provider);
        apply(provider, request);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, provider.getId(),
                AuditEvent.AuditAction.UPDATE,
                before,
                snapshot(provider)));
        return toResponse(provider);
    }

    public void delete(UUID id) {
        Provider provider = findProvider(id);
        providerRepository.delete(provider); // soft delete via @SQLDelete

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.DELETE,
                Map.of("name", provider.fullName()),
                null));
    }

    // ---- ProviderApi ----

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderSummary> findSummary(UUID providerId) {
        return providerRepository.findById(providerId)
                .map(p -> new ProviderSummary(p.getId(), p.getFirstName(), p.getLastName(),
                        p.getType().name(), p.getColor(), p.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProviderSummary> findSummaries(java.util.Set<UUID> providerIds) {
        return providerRepository.findAllById(providerIds).stream()
                .map(p -> new ProviderSummary(p.getId(), p.getFirstName(), p.getLastName(),
                        p.getType().name(), p.getColor(), p.isActive()))
                .collect(java.util.stream.Collectors.toMap(ProviderSummary::id, s -> s));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsAndActive(UUID providerId) {
        return providerRepository.findById(providerId).map(Provider::isActive).orElse(false);
    }

    // ---- helpers ----

    private void requireUniqueNpi(String npi, UUID selfId) {
        if (npi == null || npi.isBlank()) {
            return;
        }
        providerRepository.findByNpi(npi)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ConflictException("A provider with this NPI already exists");
                });
    }

    private void apply(Provider provider, ProviderRequest request) {
        provider.update(
                Provider.ProviderType.valueOf(request.type()),
                request.firstName(), request.lastName(),
                (request.npi() == null || request.npi().isBlank()) ? null : request.npi(),
                request.specialty(), request.licenseNumber(), request.licenseState(),
                request.email(), request.phone(),
                request.colorOrDefault(), request.activeOrDefault());
    }

    private Map<String, Object> snapshot(Provider provider) {
        var map = new java.util.HashMap<String, Object>();
        map.put("name", provider.fullName());
        map.put("type", provider.getType().name());
        map.put("npi", provider.getNpi());
        map.put("active", provider.isActive());
        return map;
    }

    private Provider findProvider(UUID id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", id));
    }

    private ProviderResponse toResponse(Provider p) {
        return new ProviderResponse(
                p.getId(), p.getType().name(), p.getFirstName(), p.getLastName(),
                p.getNpi(), p.getSpecialty(), p.getLicenseNumber(), p.getLicenseState(),
                p.getEmail(), p.getPhone(), p.getColor(), p.isActive(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
