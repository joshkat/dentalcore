package com.dentalcore.users.internal.service;

import com.dentalcore.infrastructure.i18n.I18nProperties;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.users.internal.dto.UserPreferencesRequest;
import com.dentalcore.users.internal.dto.UserPreferencesResponse;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Self-service language preferences, strictly scoped to the authenticated
 * user (the id always comes from the security context, never the request).
 * A null preference means "inherit the instance default".
 */
@Service
@Transactional
public class UserPreferencesService {

    private final UserRepository userRepository;
    private final I18nProperties i18n;

    public UserPreferencesService(UserRepository userRepository, I18nProperties i18n) {
        this.userRepository = userRepository;
        this.i18n = i18n;
    }

    @Transactional(readOnly = true)
    public UserPreferencesResponse getForCurrentUser() {
        return toResponse(currentUser());
    }

    public UserPreferencesResponse updateForCurrentUser(UserPreferencesRequest request) {
        User user = currentUser();
        user.updateLanguagePreferences(
                validated(request.uiLanguage(), "uiLanguage"),
                validated(request.exportLanguage(), "exportLanguage"));
        return toResponse(user);
    }

    private String validated(String value, String field) {
        if (value == null) {
            return null;
        }
        if (!I18nProperties.SUPPORTED_LANGUAGES.contains(value)) {
            throw new InvalidRequestException(
                    "'" + field + "' must be one of " + I18nProperties.SUPPORTED_LANGUAGES
                            + " or null to inherit the instance default");
        }
        return value;
    }

    private User currentUser() {
        UUID id = CurrentUser.id()
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private UserPreferencesResponse toResponse(User user) {
        String instanceDefault = i18n.defaultLanguage();
        return new UserPreferencesResponse(
                user.getUiLanguage(),
                user.getExportLanguage(),
                user.getUiLanguage() != null ? user.getUiLanguage() : instanceDefault,
                user.getExportLanguage() != null ? user.getExportLanguage() : instanceDefault);
    }
}
