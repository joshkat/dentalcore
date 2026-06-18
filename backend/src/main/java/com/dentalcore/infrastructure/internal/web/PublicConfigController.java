package com.dentalcore.infrastructure.internal.web;

import com.dentalcore.infrastructure.i18n.I18nProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, non-sensitive instance configuration. The login screen reads this
 * before authentication to pick the UI language, so it is permitAll in
 * {@code SecurityConfig} — never add anything sensitive here.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Public instance configuration (pre-auth)")
public class PublicConfigController {

    private final I18nProperties i18n;

    public PublicConfigController(I18nProperties i18n) {
        this.i18n = i18n;
    }

    @GetMapping
    @Operation(summary = "Instance default language and supported languages")
    public Map<String, Object> config() {
        return Map.of(
                "defaultLanguage", i18n.defaultLanguage(),
                "supportedLanguages", I18nProperties.SUPPORTED_LANGUAGES);
    }
}
