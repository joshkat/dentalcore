package com.dentalcore.auth.internal.service;

import com.dentalcore.auth.internal.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revocations triggered by reuse detection must commit even though the calling
 * transaction is rolled back by the auth exception thrown immediately after.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        refreshTokens.revokeFamily(familyId, Instant.now());
    }
}
