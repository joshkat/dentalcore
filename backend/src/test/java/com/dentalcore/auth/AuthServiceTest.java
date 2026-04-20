package com.dentalcore.auth;

import com.dentalcore.auth.internal.entity.RefreshToken;
import com.dentalcore.auth.internal.repository.PasswordResetTokenRepository;
import com.dentalcore.auth.internal.repository.RefreshTokenRepository;
import com.dentalcore.auth.internal.service.AuthService;
import com.dentalcore.auth.internal.service.RefreshTokenRevoker;
import com.dentalcore.auth.internal.service.TokenHasher;
import com.dentalcore.infrastructure.security.JwtService;
import com.dentalcore.infrastructure.security.SecurityProperties;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.notifications.NotificationPort;
import com.dentalcore.users.api.UserAccount;
import com.dentalcore.users.api.UserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse-battery-1";
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Mock
    private UserApi userApi;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private RefreshTokenRevoker tokenRevoker;
    @Mock
    private PasswordResetTokenRepository resetTokens;
    @Mock
    private NotificationPort notifications;
    @Mock
    private ApplicationEventPublisher events;

    private AuthService authService;
    private final TokenHasher tokenHasher = new TokenHasher();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "test-secret-key-long-enough-for-hs512-signing-purposes-0123456789abcdef"
                        .getBytes());
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(secret, Duration.ofMinutes(15), "dentalcore"),
                new SecurityProperties.RefreshToken(Duration.ofDays(7)),
                new SecurityProperties.PasswordReset(Duration.ofMinutes(30)),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15)));
        authService = new AuthService(userApi, refreshTokens, tokenRevoker, resetTokens, ENCODER,
                new JwtService(properties), tokenHasher, notifications, properties, events,
                "http://localhost:5173");
    }

    private UserAccount account(boolean active, int failedAttempts, Instant lockedUntil) {
        return new UserAccount(userId, "user@clinic.test", PASSWORD_HASH, "Pat", "Smith",
                active, Set.of("FRONT_DESK"), failedAttempts, lockedUntil);
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        when(userApi.findByEmail("user@clinic.test")).thenReturn(Optional.of(account(true, 0, null)));

        AuthService.AuthResult result = authService.login("user@clinic.test", PASSWORD);

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.response().user().roles()).containsExactly("FRONT_DESK");
        verify(userApi).resetLoginState(userId);
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void loginFailsWithWrongPassword() {
        when(userApi.findByEmail("user@clinic.test")).thenReturn(Optional.of(account(true, 0, null)));
        when(userApi.incrementFailedAttempts(userId)).thenReturn(1);

        assertThatThrownBy(() -> authService.login("user@clinic.test", "wrong-password-1"))
                .isInstanceOf(BadCredentialsException.class);
        verify(userApi, never()).lock(eq(userId), any());
    }

    @Test
    void fifthFailedAttemptLocksAccount() {
        when(userApi.findByEmail("user@clinic.test")).thenReturn(Optional.of(account(true, 4, null)));
        when(userApi.incrementFailedAttempts(userId)).thenReturn(5);

        assertThatThrownBy(() -> authService.login("user@clinic.test", "wrong-password-1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(userApi).lock(eq(userId), any(Instant.class));
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> e.action() == AuditEvent.AuditAction.ACCOUNT_LOCKED);
    }

    @Test
    void lockedAccountCannotLogInEvenWithCorrectPassword() {
        when(userApi.findByEmail("user@clinic.test"))
                .thenReturn(Optional.of(account(true, 5, Instant.now().plusSeconds(600))));

        assertThatThrownBy(() -> authService.login("user@clinic.test", PASSWORD))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void unknownEmailFailsWithoutRevealingExistence() {
        when(userApi.findByEmail("ghost@clinic.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@clinic.test", PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void refreshReuseRevokesEntireFamily() {
        String raw = tokenHasher.newToken();
        UUID familyId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(userId, tokenHasher.hash(raw), familyId,
                Instant.now().plusSeconds(3600));
        revoked.revoke();
        when(refreshTokens.findByTokenHash(tokenHasher.hash(raw))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(raw))
                .isInstanceOf(BadCredentialsException.class);

        verify(tokenRevoker).revokeFamily(familyId);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> e.action() == AuditEvent.AuditAction.TOKEN_REUSE_DETECTED);
    }

    @Test
    void validRefreshRotatesToken() {
        String raw = tokenHasher.newToken();
        UUID familyId = UUID.randomUUID();
        RefreshToken current = new RefreshToken(userId, tokenHasher.hash(raw), familyId,
                Instant.now().plusSeconds(3600));
        when(refreshTokens.findByTokenHash(tokenHasher.hash(raw))).thenReturn(Optional.of(current));
        when(userApi.findById(userId)).thenReturn(Optional.of(account(true, 0, null)));

        AuthService.AuthResult result = authService.refresh(raw);

        assertThat(current.isRevoked()).isTrue();
        assertThat(result.refreshToken()).isNotEqualTo(raw);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void passwordResetRequestForUnknownEmailDoesNothingVisible() {
        when(userApi.findByEmail("ghost@clinic.test")).thenReturn(Optional.empty());

        authService.requestPasswordReset("ghost@clinic.test");

        verify(resetTokens, never()).save(any());
    }
}
