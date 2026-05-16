package com.dentalcore.forms;

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

class FormTemplateIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-formtpl@clinic.test";
    private static final String DENTIST_EMAIL = "dentist-formtpl@clinic.test";
    private static final String READONLY_EMAIL = "readonly-formtpl@clinic.test";
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
    private HttpHeaders admin;
    private HttpHeaders dentist;
    private HttpHeaders readOnly;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
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

    private Map<String, Object> textField(String key, String label, boolean required) {
        return Map.of("key", key, "label", label, "type", "TEXT", "required", required);
    }

    private Map<String, Object> templateBody(String name, List<Map<String, Object>> fields) {
        return Map.of("name", name, "description", "A test form", "fields", fields);
    }

    @Test
    void templateCrudWorks() {
        String name = "Medical History " + SEQ.incrementAndGet();
        ResponseEntity<Map<String, Object>> created = api.post("/api/v1/forms/templates", admin,
                templateBody(name, List.of(
                        textField("allergies", "Known allergies", true),
                        Map.of("key", "smoker", "label", "Do you smoke?", "type", "SELECT",
                                "required", true, "options", List.of("Yes", "No")))));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name")).isEqualTo(name);
        assertThat(created.getBody().get("active")).isEqualTo(true);
        assertThat(created.getBody().get("createdAt")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields =
                (List<Map<String, Object>>) created.getBody().get("fields");
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).get("key")).isEqualTo("allergies");
        assertThat(fields.get(1).get("options")).isEqualTo(List.of("Yes", "No"));
        String id = (String) created.getBody().get("id");

        // listing includes it, readable by any authenticated user
        ResponseEntity<List<Map<String, Object>>> list =
                api.getList("/api/v1/forms/templates", readOnly);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).anyMatch(t -> id.equals(t.get("id")));

        // update
        String renamed = name + " v2";
        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/forms/templates/" + id, admin,
                templateBody(renamed, List.of(textField("allergies", "Allergies", true))));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo(renamed);

        // hard delete (no instances exist)
        assertThat(api.delete("/api/v1/forms/templates/" + id, admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.getList("/api/v1/forms/templates", admin).getBody())
                .noneMatch(t -> id.equals(t.get("id")));
    }

    @Test
    void duplicateFieldKeysAreRejected() {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/forms/templates", admin,
                templateBody("Dup Keys " + SEQ.incrementAndGet(), List.of(
                        textField("name", "First name", true),
                        textField("name", "Last name", true))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void selectFieldWithoutOptionsIsRejected() {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/forms/templates", admin,
                templateBody("No Options " + SEQ.incrementAndGet(), List.of(
                        Map.of("key", "choice", "label", "Pick one", "type", "SELECT",
                                "required", true))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blankLabelIsRejected() {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/forms/templates", admin,
                templateBody("Blank Label " + SEQ.incrementAndGet(), List.of(
                        Map.of("key", "x", "label", " ", "type", "TEXT", "required", false))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteDeactivatesWhenInstancesExist() {
        String name = "Consent " + SEQ.incrementAndGet();
        String templateId = (String) api.post("/api/v1/forms/templates", admin,
                templateBody(name, List.of(textField("consent", "I consent", true))))
                .getBody().get("id");
        String patientId = (String) api.post("/api/v1/patients", dentist, Map.of(
                "firstName", "Form", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-01-01", "sex", "FEMALE")).getBody().get("id");
        assertThat(api.post("/api/v1/forms/instances", dentist,
                Map.of("patientId", patientId, "templateId", templateId)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(api.delete("/api/v1/forms/templates/" + templateId, admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // still listed but no longer active
        assertThat(api.getList("/api/v1/forms/templates", admin).getBody())
                .anyMatch(t -> templateId.equals(t.get("id"))
                        && Boolean.FALSE.equals(t.get("active")));
    }

    @Test
    void mutationsRequireAdmin() {
        Map<String, Object> body = templateBody("RBAC " + SEQ.incrementAndGet(),
                List.of(textField("q", "Question", false)));
        assertThat(api.post("/api/v1/forms/templates", dentist, body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/forms/templates", readOnly, body).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.delete("/api/v1/forms/templates/" + java.util.UUID.randomUUID(), dentist)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
