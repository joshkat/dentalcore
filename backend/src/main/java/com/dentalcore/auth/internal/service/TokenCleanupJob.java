package com.dentalcore.auth.internal.service;

import com.dentalcore.auth.internal.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        // Keep expired tokens for 7 days so reuse-detection evidence survives briefly.
        refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
    }
}
