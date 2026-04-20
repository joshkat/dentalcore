package com.dentalcore.auth;

import com.dentalcore.shared.notifications.NotificationPort;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class AuthFlowIntegrationTest extends IntegrationTest {

    private static final String EMAIL = "frontdesk@clinic.test";
    private static final String PASSWORD = "front-desk-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private NotificationPort notifications;

    @BeforeEach
    void seedUser() {
        // Recreate only this class's user (fresh lockout/token state per test);
        // never deleteAll — other suites' users are referenced by clinical notes.
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
        User user = new User(EMAIL, passwordEncoder.encode(PASSWORD), "Front", "Desk", null);
        user.setRoles(Set.of(roleRepository.findByName("FRONT_DESK").orElseThrow()));
        userRepository.save(user);
    }

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() {
        ResponseEntity<Map<String, Object>> response = login(EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        assertThat(refreshCookie(response)).isNotNull().contains("HttpOnly");

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertThat(user.get("email")).isEqualTo(EMAIL);
    }

    @Test
    void accessTokenAuthorizesProtectedEndpoint() {
        String accessToken = (String) login(EMAIL, PASSWORD).getBody().get("accessToken");

        ResponseEntity<Map<String, Object>> me = exchange(
                "/api/v1/users/me", HttpMethod.GET, bearer(accessToken), null);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(EMAIL);
    }

    @Test
    void protectedEndpointRejectsAnonymousRequests() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/v1/users/me", HttpMethod.GET, new HttpHeaders(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThat(login(EMAIL, "totally-wrong-pass-1").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accountLocksAfterFiveFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "totally-wrong-pass-1");
        }
        ResponseEntity<Map<String, Object>> locked = login(EMAIL, PASSWORD);

        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(locked.getBody().get("title")).isEqualTo("Account Locked");
    }

    @Test
    void refreshRotatesTokenAndOldTokenIsRejected() {
        ResponseEntity<Map<String, Object>> loginResponse = login(EMAIL, PASSWORD);
        String originalCookie = refreshCookieValue(loginResponse);

        ResponseEntity<Map<String, Object>> refreshed = exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, withCookie(originalCookie), null);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshCookieValue(refreshed)).isNotEqualTo(originalCookie);

        // Replaying the rotated (now revoked) token must fail — reuse detection.
        ResponseEntity<Map<String, Object>> replay = exchange(
                "/api/v1/auth/refresh", HttpMethod.POST, withCookie(originalCookie), null);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // The reuse must also have killed the newest token in the family.
        ResponseEntity<Map<String, Object>> afterReuse = exchange(
                "/api/v1/auth/refresh", HttpMethod.POST,
                withCookie(refreshCookieValue(refreshed)), null);
        assertThat(afterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordResetFlowAllowsLoginWithNewPassword() {
        ResponseEntity<Map<String, Object>> request = exchange(
                "/api/v1/auth/forgot-password", HttpMethod.POST, json(),
                Map.of("email", EMAIL));
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendPasswordResetLink(eq(EMAIL), urlCaptor.capture());
        String token = urlCaptor.getValue().substring(urlCaptor.getValue().indexOf("token=") + 6);

        String newPassword = "brand-new-password-9";
        ResponseEntity<Map<String, Object>> reset = exchange(
                "/api/v1/auth/reset-password", HttpMethod.POST, json(),
                Map.of("token", token, "newPassword", newPassword));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(EMAIL, newPassword).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Reset tokens are single-use.
        ResponseEntity<Map<String, Object>> replay = exchange(
                "/api/v1/auth/reset-password", HttpMethod.POST, json(),
                Map.of("token", token, "newPassword", "another-password-77"));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPasswordNeverRevealsWhetherAccountExists() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/v1/auth/forgot-password", HttpMethod.POST, json(),
                Map.of("email", "ghost@clinic.test"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return exchange("/api/v1/auth/login", HttpMethod.POST, json(),
                Map.of("email", email, "password", password));
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method,
                                                         HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = json();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private HttpHeaders withCookie(String refreshCookieValue) {
        HttpHeaders headers = json();
        headers.add(HttpHeaders.COOKIE, "dc_refresh=" + refreshCookieValue);
        return headers;
    }

    private String refreshCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream().filter(c -> c.startsWith("dc_refresh=")).findFirst().orElse(null);
    }

    private String refreshCookieValue(ResponseEntity<?> response) {
        String cookie = refreshCookie(response);
        assertThat(cookie).isNotNull();
        return cookie.substring("dc_refresh=".length(), cookie.indexOf(';'));
    }
}
