package com.dentalcore.auth.internal.dto;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        AuthUser user
) {
    public record AuthUser(UUID id, String email, String firstName, String lastName, Set<String> roles) {
    }
}
