package com.dentalcore.patients.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record MedicalAlertResponse(
        UUID id,
        String type,
        String description,
        String severity,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
