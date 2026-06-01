package com.dentalcore.infrastructure.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Instance-wide internationalization settings. The default language is what
 * every user and export inherits unless a personal preference overrides it.
 * Validated at bind time so a misconfigured instance fails fast at startup.
 */
@ConfigurationProperties(prefix = "dentalcore.i18n")
public record I18nProperties(String defaultLanguage) {

    /** Languages the product ships bundles for; order is the advertised order. */
    public static final List<String> SUPPORTED_LANGUAGES = List.of("en", "es");

    public I18nProperties {
        if (defaultLanguage == null || !SUPPORTED_LANGUAGES.contains(defaultLanguage)) {
            throw new IllegalArgumentException(
                    "dentalcore.i18n.default-language must be one of "
                            + SUPPORTED_LANGUAGES + " but was '" + defaultLanguage + "'");
        }
    }
}
