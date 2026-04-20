package com.dentalcore.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        UUID userId,
        String entityType,
        UUID entityId,
        String action,
        Map<String, Object> previousValue,
        Map<String, Object> newValue,
        Instant occurredAt
) {
}
