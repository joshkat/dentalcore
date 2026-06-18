package com.dentalcore.users.internal.dto;

/**
 * {@code uiLanguage}/{@code exportLanguage} are the stored preferences
 * (null = inherit); the {@code effective*} fields resolve the preference
 * against the instance default and are always present.
 */
public record UserPreferencesResponse(
        String uiLanguage,
        String exportLanguage,
        String effectiveUiLanguage,
        String effectiveExportLanguage
) {
}
