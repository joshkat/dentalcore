package com.dentalcore.infrastructure.security;

import com.dentalcore.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "a-very-long-test-secret-key-that-is-long-enough-for-hs512-signing-0123456789"
                    .getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(properties(SECRET, Duration.ofMinutes(15)));
    }

    @Test
    void roundTripPreservesIdentityAndRoles() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "dentist@clinic.test", Set.of("DENTIST", "ADMIN"));

        String token = jwtService.generateAccessToken(user);
        Optional<AuthenticatedUser> parsed = jwtService.parse(token);

        assertThat(parsed).hasValueSatisfying(p -> {
            assertThat(p.id()).isEqualTo(user.id());
            assertThat(p.email()).isEqualTo(user.email());
            assertThat(p.roles()).containsExactlyInAnyOrder("DENTIST", "ADMIN");
        });
    }

    @Test
    void tamperedTokenIsRejected() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "a@b.test", Set.of("READ_ONLY"));
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.parse(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(properties(SECRET, Duration.ofMillis(-1000)));
        String token = shortLived.generateAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "a@b.test", Set.of("READ_ONLY")));

        assertThat(shortLived.parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String otherSecret = Base64.getEncoder().encodeToString(
                "another-very-long-secret-key-that-is-also-long-enough-for-hs512-XXXXXXXX"
                        .getBytes());
        JwtService other = new JwtService(properties(otherSecret, Duration.ofMinutes(15)));
        String token = other.generateAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "a@b.test", Set.of("ADMIN")));

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(properties("", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void garbageInputIsRejected() {
        assertThat(jwtService.parse("not-a-jwt")).isEmpty();
    }

    private SecurityProperties properties(String secret, Duration accessTtl) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, accessTtl, "dentalcore"),
                new SecurityProperties.RefreshToken(Duration.ofDays(7)),
                new SecurityProperties.PasswordReset(Duration.ofMinutes(30)),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)));
    }
}
