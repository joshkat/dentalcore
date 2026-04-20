package com.dentalcore.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dentalcore.security")
public record SecurityProperties(
        Jwt jwt,
        RefreshToken refreshToken,
        PasswordReset passwordReset,
        Lockout lockout
) {
    public record Jwt(String secret, Duration accessTokenTtl, String issuer) {
    }

    public record RefreshToken(Duration ttl) {
    }

    public record PasswordReset(Duration ttl) {
    }

    public record Lockout(int maxFailedAttempts, Duration lockDuration) {
    }
}
