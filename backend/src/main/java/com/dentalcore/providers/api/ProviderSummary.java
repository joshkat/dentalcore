package com.dentalcore.providers.api;

import java.util.UUID;

/** Cross-module view of a provider — used by scheduling and reporting. */
public record ProviderSummary(
        UUID id,
        String firstName,
        String lastName,
        String type,
        String color,
        boolean active
) {
}
