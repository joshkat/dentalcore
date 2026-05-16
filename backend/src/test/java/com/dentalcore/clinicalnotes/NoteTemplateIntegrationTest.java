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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class NoteTemplateIntegrationTest extends IntegrationTest {

    private static final String DENTIST_EMAIL = "dentist-notetpl@clinic.test";
    private static final String FRONTDESK_EMAIL = "frontdesk-notetpl@clinic.test";
    private static final String READONLY_EMAIL = "readonly-notetpl@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

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

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(FRONTDESK_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONTDESK_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    @Test
    void crudAndPromptExtractionWork() {
        String name = "Composite Filling " + SEQ.incrementAndGet();
        ResponseEntity<Map<String, Object>> created = api.post(
                "/api/v1/clinical-notes/templates", dentist, Map.of(
                        "name", name, "noteType", "PROCEDURE",
                        "body", "Placed {{material}} on tooth {{tooth}}. "
                                + "Re-checked tooth {{tooth}} before dismissal."));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name")).isEqualTo(name);
        assertThat(created.getBody().get("noteType")).isEqualTo("PROCEDURE");
        assertThat(created.getBody().get("active")).isEqualTo(true);
        assertThat(created.getBody().get("createdAt")).isNotNull();
        // order-preserved, de-duplicated placeholder keys
        assertThat(created.getBody().get("prompts")).isEqualTo(List.of("material", "tooth"));
        String id = (String) created.getBody().get("id");

        // any authenticated user can list templates
        ResponseEntity<List<Map<String, Object>>> list =
                api.getList("/api/v1/clinical-notes/templates", frontDesk);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).anyMatch(t -> id.equals(t.get("id")));

        // update re-extracts the prompts
        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/clinical-notes/templates/" + id, dentist, Map.of(
                        "name", name, "noteType", "EXAM",
                        "body", "Tooth {{tooth}} with {{material}}, shade {{shade}}."));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("noteType")).isEqualTo("EXAM");
        assertThat(updated.getBody().get("prompts"))
                .isEqualTo(List.of("tooth", "material", "shade"));

        // delete
        assertThat(api.delete("/api/v1/clinical-notes/templates/" + id, dentist)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.getList("/api/v1/clinical-notes/templates", dentist).getBody())
                .noneMatch(t -> id.equals(t.get("id")));
    }

    @Test
    void unknownNoteTypeIsRejected() {
        assertThat(api.post("/api/v1/clinical-notes/templates", dentist, Map.of(
                "name", "Bad Type " + SEQ.incrementAndGet(), "noteType", "SURGERY",
                "body", "x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mutationsRequireClinicalRoles() {
        Map<String, Object> body = Map.of("name", "RBAC " + SEQ.incrementAndGet(),
                "noteType", "PROGRESS", "body", "{{tooth}}");
        assertThat(api.post("/api/v1/clinical-notes/templates", frontDesk, body)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/clinical-notes/templates", readOnly, body)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // but reads stay open to any authenticated user
        assertThat(api.getList("/api/v1/clinical-notes/templates", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
