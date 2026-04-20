package com.dentalcore.users;

import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin@clinic.test";
    private static final String FRONT_EMAIL = "front@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        // Delete only this class's users — other suites' users may be referenced
        // by clinical notes and must survive.
        for (String email : new String[]{ADMIN_EMAIL, FRONT_EMAIL, "hygienist@clinic.test",
                "weak@clinic.test"}) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
        User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "Ada", "Admin", null);
        admin.setRoles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()));
        userRepository.save(admin);

        User front = new User(FRONT_EMAIL, passwordEncoder.encode(PASSWORD), "Fred", "Front", null);
        front.setRoles(Set.of(roleRepository.findByName("FRONT_DESK").orElseThrow()));
        userRepository.save(front);
    }

    @Test
    void adminCanListUsers() {
        ResponseEntity<Map<String, Object>> response =
                get("/api/v1/users?size=100", token(ADMIN_EMAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).extracting(u -> u.get("email"))
                .contains(ADMIN_EMAIL, FRONT_EMAIL);
    }

    @Test
    void nonAdminCannotListUsers() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/users", token(FRONT_EMAIL));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCreateUserAndNewUserCanLogIn() {
        ResponseEntity<Map<String, Object>> created = exchange(
                "/api/v1/users", HttpMethod.POST, token(ADMIN_EMAIL),
                Map.of("email", "hygienist@clinic.test",
                        "password", "hygienist-pass-12",
                        "firstName", "Holly",
                        "lastName", "Hygienist",
                        "roles", List.of("HYGIENIST")));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("roles")).isEqualTo(List.of("HYGIENIST"));

        ResponseEntity<Map<String, Object>> login = exchange(
                "/api/v1/auth/login", HttpMethod.POST, json(),
                Map.of("email", "hygienist@clinic.test", "password", "hygienist-pass-12"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/v1/users", HttpMethod.POST, token(ADMIN_EMAIL),
                Map.of("email", FRONT_EMAIL,
                        "password", "any-valid-pass-12",
                        "firstName", "Dup",
                        "lastName", "User",
                        "roles", List.of("READ_ONLY")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void weakPasswordFailsValidation() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/v1/users", HttpMethod.POST, token(ADMIN_EMAIL),
                Map.of("email", "weak@clinic.test",
                        "password", "short1",
                        "firstName", "Weak",
                        "lastName", "Password",
                        "roles", List.of("READ_ONLY")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("errors");
    }

    // --- helpers ---

    private HttpHeaders token(String email) {
        ResponseEntity<Map<String, Object>> login = exchange(
                "/api/v1/auth/login", HttpMethod.POST, json(),
                Map.of("email", email, "password", PASSWORD));
        HttpHeaders headers = json();
        headers.setBearerAuth((String) login.getBody().get("accessToken"));
        return headers;
    }

    private ResponseEntity<Map<String, Object>> get(String path, HttpHeaders headers) {
        return exchange(path, HttpMethod.GET, headers, null);
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method,
                                                         HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
