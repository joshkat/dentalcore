package com.dentalcore.clinicalnotes;

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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalNoteIntegrationTest extends IntegrationTest {

    private static final String DENTIST_EMAIL = "dentist-notes@clinic.test";
    private static final String FRONT_EMAIL = "front-notes@clinic.test";
    private static final String READONLY_EMAIL = "readonly-notes@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private HttpHeaders dentist;
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private String patientId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", dentist, Map.of(
                "firstName", "Note", "lastName", "Patient",
                "dateOfBirth", "1970-07-07", "sex", "MALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String createNote(String body) {
        ResponseEntity<Map<String, Object>> response = api.post(
                "/api/v1/clinical-notes?patientId=" + patientId, dentist,
                Map.of("noteType", "EXAM", "body", body));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void unsignedNoteCanBeEditedAndDeleted() {
        String noteId = createNote("Initial findings");

        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/clinical-notes/" + noteId, dentist,
                Map.of("noteType", "EXAM", "body", "Amended findings"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("body")).isEqualTo("Amended findings");

        assertThat(api.delete("/api/v1/clinical-notes/" + noteId, dentist).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void signedNoteIsImmutableAndPermanent() {
        String noteId = createNote("Tooth 14 MOD composite placed");

        ResponseEntity<Map<String, Object>> signed = api.post(
                "/api/v1/clinical-notes/" + noteId + "/sign", dentist, null);
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signed.getBody().get("signedAt")).isNotNull();
        assertThat(signed.getBody().get("signedBy")).isNotNull();

        // edits, re-signing, and deletion all rejected
        assertThat(api.put("/api/v1/clinical-notes/" + noteId, dentist,
                Map.of("noteType", "EXAM", "body", "Tampered")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.post("/api/v1/clinical-notes/" + noteId + "/sign", dentist, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.delete("/api/v1/clinical-notes/" + noteId, dentist).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyClinicalAndReadOnlyRolesCanReadNotes() {
        createNote("Clinical content");

        // read-only can read but not write
        ResponseEntity<Map<String, Object>> list = api.get(
                "/api/v1/clinical-notes?patientId=" + patientId, readOnly);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/clinical-notes?patientId=" + patientId, readOnly,
                Map.of("noteType", "PROGRESS", "body", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // front desk has no access to clinical documentation
        assertThat(api.get("/api/v1/clinical-notes?patientId=" + patientId, frontDesk)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/clinical-notes?patientId=" + patientId, frontDesk,
                Map.of("noteType", "PROGRESS", "body", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
