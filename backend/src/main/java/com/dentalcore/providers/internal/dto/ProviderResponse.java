package com.dentalcore.providers.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record ProviderResponse(
        UUID id,
        String type,
        String firstName,
        String lastName,
        String npi,
        String specialty,
        String licenseNumber,
        String licenseState,
        String email,
        String phone,
        String color,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
