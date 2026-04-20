package com.dentalcore.providers;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-providers@clinic.test";
    private static final String HYGIENIST_EMAIL = "hygienist-providers@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong NPI_SEQ = new AtomicLong(1_234_567_000L);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private HttpHeaders admin;
    private HttpHeaders hygienist;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(HYGIENIST_EMAIL, "HYGIENIST");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        hygienist = api.login(HYGIENIST_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private Map<String, Object> providerBody(String lastName, String npi) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "DENTIST");
        body.put("firstName", "Doc");
        body.put("lastName", lastName);
        body.put("npi", npi);
        body.put("specialty", "General Dentistry");
        body.put("licenseNumber", "D-12345");
        body.put("licenseState", "CA");
        body.put("color", "#16a34a");
        return body;
    }

    private String nextNpi() {
        return String.valueOf(NPI_SEQ.incrementAndGet());
    }

    @Test
    void adminCanCreateUpdateAndListProviders() {
        ResponseEntity<Map<String, Object>> created = api.post(
                "/api/v1/providers", admin, providerBody("Crentist", nextNpi()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(created.getBody().get("color")).isEqualTo("#16a34a");

        Map<String, Object> update = providerBody("Crentist", (String) created.getBody().get("npi"));
        update.put("specialty", "Orthodontics");
        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/providers/" + id, admin, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("specialty")).isEqualTo("Orthodontics");

        ResponseEntity<Map<String, Object>> list = api.get("/api/v1/providers", hygienist);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) list.getBody().get("content")).isNotEmpty();
    }

    @Test
    void nonAdminCannotWriteProviders() {
        assertThat(api.post("/api/v1/providers", hygienist, providerBody("Denied", nextNpi()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateNpiIsRejected() {
        String npi = nextNpi();
        assertThat(api.post("/api/v1/providers", admin, providerBody("First", npi))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(api.post("/api/v1/providers", admin, providerBody("Second", npi))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidNpiAndColorAreRejected() {
        Map<String, Object> badNpi = providerBody("BadNpi", "12345");
        assertThat(api.post("/api/v1/providers", admin, badNpi).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> badColor = providerBody("BadColor", nextNpi());
        badColor.put("color", "green");
        assertThat(api.post("/api/v1/providers", admin, badColor).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void softDeletedProviderDisappears() {
        ResponseEntity<Map<String, Object>> created = api.post(
                "/api/v1/providers", admin, providerBody("Vanish", nextNpi()));
        String id = (String) created.getBody().get("id");

        assertThat(api.delete("/api/v1/providers/" + id, admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.get("/api/v1/providers/" + id, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
