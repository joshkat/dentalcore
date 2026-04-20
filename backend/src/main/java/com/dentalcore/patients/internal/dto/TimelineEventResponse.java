package com.dentalcore.patients.internal.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TimelineEventResponse(
        UUID id,
        String action,
        UUID userId,
        Map<String, Object> previousValue,
        Map<String, Object> newValue,
        Instant occurredAt
) {
}
