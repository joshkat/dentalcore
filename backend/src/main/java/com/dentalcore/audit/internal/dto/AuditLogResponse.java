package com.dentalcore.audit.internal.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        String entityType,
        UUID entityId,
        String action,
        Map<String, Object> previousValue,
        Map<String, Object> newValue,
        String ipAddress,
        Instant occurredAt
) {
}
