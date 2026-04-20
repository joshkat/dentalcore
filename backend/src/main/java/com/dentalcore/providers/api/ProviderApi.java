package com.dentalcore.providers.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public interface of the providers module. */
public interface ProviderApi {

    Optional<ProviderSummary> findSummary(UUID providerId);

    Map<UUID, ProviderSummary> findSummaries(Set<UUID> providerIds);

    boolean existsAndActive(UUID providerId);
}
