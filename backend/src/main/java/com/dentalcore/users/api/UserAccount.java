package com.dentalcore.users.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module view of a user account. Exposes exactly what the auth module
 * needs for credential checks — never the JPA entity.
 */
public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        boolean active,
        Set<String> roles,
        int failedAttempts,
        Instant lockedUntil
) {
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
