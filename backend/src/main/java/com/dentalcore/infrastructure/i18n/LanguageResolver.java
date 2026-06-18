package com.dentalcore.infrastructure.i18n;

import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.users.api.UserApi;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the language to render an export (PDF) in. The chain is:
 *
 * <ol>
 *   <li>an explicit {@code ?lang=en|es} request parameter (validated, 400 otherwise)</li>
 *   <li>the requesting user's {@code export_language} preference</li>
 *   <li>the instance default ({@code dentalcore.i18n.default-language})</li>
 * </ol>
 */
@Component
public class LanguageResolver {

    private final UserApi userApi;
    private final I18nProperties properties;

    public LanguageResolver(UserApi userApi, I18nProperties properties) {
        this.userApi = userApi;
        this.properties = properties;
    }

    /** Explicit request param &gt; current user's export preference &gt; instance default. */
    public String resolve(String requestedLang) {
        if (requestedLang != null && !requestedLang.isBlank()) {
            return validated(requestedLang);
        }
        return CurrentUser.id()
                .map(this::resolveForUser)
                .orElse(properties.defaultLanguage());
    }

    /**
     * A specific user's effective export language (preference, else instance
     * default). Used where there is no request context per rendered item,
     * e.g. batch statement runs use the run creator's language for every PDF.
     */
    public String resolveForUser(UUID userId) {
        if (userId == null) {
            return properties.defaultLanguage();
        }
        return userApi.exportLanguageOf(userId).orElse(properties.defaultLanguage());
    }

    private String validated(String lang) {
        String normalized = lang.trim().toLowerCase();
        if (!I18nProperties.SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new InvalidRequestException(
                    "Unsupported language '" + lang + "'; supported: "
                            + String.join(", ", I18nProperties.SUPPORTED_LANGUAGES));
        }
        return normalized;
    }
}
