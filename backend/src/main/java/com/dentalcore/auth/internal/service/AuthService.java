package com.dentalcore.auth.internal.service;

import com.dentalcore.auth.internal.dto.AuthResponse;
import com.dentalcore.auth.internal.entity.PasswordResetToken;
import com.dentalcore.auth.internal.entity.RefreshToken;
import com.dentalcore.auth.internal.repository.PasswordResetTokenRepository;
import com.dentalcore.auth.internal.repository.RefreshTokenRepository;
import com.dentalcore.infrastructure.security.JwtService;
import com.dentalcore.infrastructure.security.SecurityProperties;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.notifications.NotificationPort;
import com.dentalcore.shared.security.AuthenticatedUser;
import com.dentalcore.users.api.UserAccount;
import com.dentalcore.users.api.UserApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    /** Decoy hash so unknown emails take as long as wrong passwords (timing attack mitigation). */
    private final String dummyPasswordHash;

    private final UserApi userApi;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenRevoker tokenRevoker;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final NotificationPort notifications;
    private final SecurityProperties properties;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;

    public AuthService(UserApi userApi,
                       RefreshTokenRepository refreshTokens,
                       RefreshTokenRevoker tokenRevoker,
                       PasswordResetTokenRepository resetTokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHasher tokenHasher,
                       NotificationPort notifications,
                       SecurityProperties properties,
                       ApplicationEventPublisher events,
                       @Value("${dentalcore.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.userApi = userApi;
        this.refreshTokens = refreshTokens;
        this.tokenRevoker = tokenRevoker;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHasher = tokenHasher;
        this.notifications = notifications;
        this.properties = properties;
        this.events = events;
        this.frontendUrl = frontendUrl;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    public AuthResult login(String email, String password) {
        UserAccount account = userApi.findByEmail(email).orElse(null);
        if (account == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new BadCredentialsException("Invalid credentials");
        }
        if (account.isLocked()) {
            events.publishEvent(AuditEvent.of(account.id(), "User", account.id(),
                    AuditEvent.AuditAction.LOGIN_FAILED));
            throw new LockedException("Account is temporarily locked. Try again later.");
        }
        if (!account.active() || !passwordEncoder.matches(password, account.passwordHash())) {
            handleFailedLogin(account);
            throw new BadCredentialsException("Invalid credentials");
        }

        userApi.resetLoginState(account.id());
        events.publishEvent(AuditEvent.of(account.id(), "User", account.id(),
                AuditEvent.AuditAction.LOGIN));
        return issueTokens(account, UUID.randomUUID());
    }

    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (token.isRevoked()) {
            // A previously rotated token is being replayed — assume theft, kill the family.
            tokenRevoker.revokeFamily(token.getFamilyId());
            events.publishEvent(AuditEvent.of(token.getUserId(), "RefreshToken", token.getId(),
                    AuditEvent.AuditAction.TOKEN_REUSE_DETECTED));
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (token.isExpired()) {
            throw new BadCredentialsException("Refresh token expired");
        }

        UserAccount account = userApi.findById(token.getUserId())
                .filter(UserAccount::active)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        token.revoke();
        events.publishEvent(AuditEvent.of(account.id(), "User", account.id(),
                AuditEvent.AuditAction.TOKEN_REFRESH));
        return issueTokens(account, token.getFamilyId());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(tokenHasher.hash(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            events.publishEvent(AuditEvent.of(token.getUserId(), "User", token.getUserId(),
                    AuditEvent.AuditAction.LOGOUT));
        });
    }

    public void requestPasswordReset(String email) {
        // Always succeeds from the caller's perspective — no account enumeration.
        userApi.findByEmail(email).filter(UserAccount::active).ifPresent(account -> {
            resetTokens.invalidateAllForUser(account.id(), Instant.now());
            String raw = tokenHasher.newToken();
            resetTokens.save(new PasswordResetToken(
                    account.id(),
                    tokenHasher.hash(raw),
                    Instant.now().plus(properties.passwordReset().ttl())));
            notifications.sendPasswordResetLink(account.email(),
                    frontendUrl + "/reset-password?token=" + raw);
            events.publishEvent(AuditEvent.of(account.id(), "User", account.id(),
                    AuditEvent.AuditAction.PASSWORD_RESET_REQUESTED));
        });
    }

    public void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokens.findByTokenHash(tokenHasher.hash(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        token.markUsed();
        userApi.updatePassword(token.getUserId(), passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(token.getUserId(), Instant.now());
        events.publishEvent(AuditEvent.of(token.getUserId(), "User", token.getUserId(),
                AuditEvent.AuditAction.PASSWORD_RESET_COMPLETED));
    }

    public long refreshTokenTtlSeconds() {
        return properties.refreshToken().ttl().toSeconds();
    }

    private void handleFailedLogin(UserAccount account) {
        int attempts = userApi.incrementFailedAttempts(account.id());
        events.publishEvent(new AuditEvent(account.id(), "User", account.id(),
                AuditEvent.AuditAction.LOGIN_FAILED,
                null, Map.of("failedAttempts", attempts)));
        if (attempts >= properties.lockout().maxFailedAttempts()) {
            userApi.lock(account.id(), Instant.now().plus(properties.lockout().lockDuration()));
            events.publishEvent(AuditEvent.of(account.id(), "User", account.id(),
                    AuditEvent.AuditAction.ACCOUNT_LOCKED));
        }
    }

    private AuthResult issueTokens(UserAccount account, UUID familyId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                account.id(), account.email(), account.roles());
        String accessToken = jwtService.generateAccessToken(principal);

        String rawRefresh = tokenHasher.newToken();
        refreshTokens.save(new RefreshToken(
                account.id(),
                tokenHasher.hash(rawRefresh),
                familyId,
                Instant.now().plus(properties.refreshToken().ttl())));

        AuthResponse response = new AuthResponse(
                accessToken,
                properties.jwt().accessTokenTtl().toSeconds(),
                new AuthResponse.AuthUser(account.id(), account.email(),
                        account.firstName(), account.lastName(), account.roles()));
        return new AuthResult(response, rawRefresh);
    }
}
