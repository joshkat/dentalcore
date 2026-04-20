package com.dentalcore.shared.events;

import java.util.Map;
import java.util.UUID;

/**
 * Published by any module to record a significant action. Consumed by the audit module
 * so producers never depend on audit internals.
 */
public record AuditEvent(
        UUID userId,
        String entityType,
        UUID entityId,
        AuditAction action,
        Map<String, Object> previousValue,
        Map<String, Object> newValue
) {
    public enum AuditAction {
        CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED, LOGOUT,
        TOKEN_REFRESH, TOKEN_REUSE_DETECTED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED,
        ACCOUNT_LOCKED, STATUS_CHANGE
    }

    public static AuditEvent of(UUID userId, String entityType, UUID entityId, AuditAction action) {
        return new AuditEvent(userId, entityType, entityId, action, null, null);
    }
}
