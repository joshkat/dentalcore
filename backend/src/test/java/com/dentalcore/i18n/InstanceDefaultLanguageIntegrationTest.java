package com.dentalcore.i18n;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the app with a Spanish instance default to prove the public config
 * endpoint reflects the property and that unset preferences inherit it.
 */
@TestPropertySource(properties = "dentalcore.i18n.default-language=es")
class InstanceDefaultLanguageIntegrationTest extends IntegrationTest {

    private static final String EMAIL = "readonly-i18n-default@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void configReflectsInstanceDefaultAndPreferencesInheritIt() {
        ResponseEntity<Map<String, Object>> config = rest.exchange(
                "/api/v1/config", HttpMethod.GET, HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config.getBody().get("defaultLanguage")).isEqualTo("es");

        if (userRepository.findByEmailIgnoreCase(EMAIL).isEmpty()) {
            User user = new User(EMAIL, passwordEncoder.encode(PASSWORD),
                    "Test", "READ_ONLY", null);
            user.setRoles(Set.of(roleRepository.findByName("READ_ONLY").orElseThrow()));
            userRepository.save(user);
        }
        ApiTestClient api = new ApiTestClient(rest);
        HttpHeaders user = api.login(EMAIL, PASSWORD);
        Map<String, Object> preferences =
                api.get("/api/v1/users/me/preferences", user).getBody();
        assertThat(preferences.get("uiLanguage")).isNull();
        assertThat(preferences.get("effectiveUiLanguage")).isEqualTo("es");
        assertThat(preferences.get("effectiveExportLanguage")).isEqualTo("es");
    }
}
