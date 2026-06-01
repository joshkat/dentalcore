package com.dentalcore.users.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Public interface of the users module, consumed by other modules (auth).
 */
public interface UserApi {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(UUID id);

    /** Increments the failed-login counter and returns the new count. */
    int incrementFailedAttempts(UUID userId);

    void lock(UUID userId, Instant until);

    /** Clears failed attempts and any lockout after a successful login. */
    void resetLoginState(UUID userId);

    void updatePassword(UUID userId, String newPasswordHash);

    /**
     * The user's PDF/export language preference (en|es), empty when the user
     * is unknown or has no preference (i.e. inherits the instance default).
     */
    Optional<String> exportLanguageOf(UUID userId);
}
