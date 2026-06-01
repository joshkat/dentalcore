package com.dentalcore.users.internal.dto;

/**
 * Self-service language preferences. {@code null} resets a preference to
 * "inherit the instance default".
 */
public record UserPreferencesRequest(
        String uiLanguage,
        String exportLanguage
) {
}
