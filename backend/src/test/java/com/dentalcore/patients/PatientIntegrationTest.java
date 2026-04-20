package com.dentalcore.patients;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PatientIntegrationTest extends IntegrationTest {

    private static final String FRONT_EMAIL = "front-patients@clinic.test";
    private static final String READONLY_EMAIL = "readonly-patients@clinic.test";
    private static final String ADMIN_EMAIL = "admin-patients@clinic.test";
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
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private HttpHeaders admin;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        seedUser(ADMIN_EMAIL, "ADMIN");
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private Map<String, Object> patientBody(String firstName, String lastName) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("dateOfBirth", "1985-04-12");
        body.put("sex", "FEMALE");
        body.put("email", (firstName + "." + lastName + "@example.test").toLowerCase());
        body.put("phones", List.of(
                Map.of("type", "MOBILE", "number", "555-867-5309", "primary", true)));
        return body;
    }

    private String createPatient(String firstName, String lastName) {
        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/patients", frontDesk, patientBody(firstName, lastName));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void frontDeskCanRegisterAndFetchPatient() {
        String id = createPatient("Maria", "Gonzalez");

        ResponseEntity<Map<String, Object>> fetched =
                api.get("/api/v1/patients/" + id, frontDesk);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("firstName")).isEqualTo("Maria");
        assertThat(fetched.getBody().get("status")).isEqualTo("ACTIVE");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phones = (List<Map<String, Object>>) fetched.getBody().get("phones");
        assertThat(phones).hasSize(1);
        assertThat(phones.get(0).get("number")).isEqualTo("555-867-5309");
    }

    @Test
    void readOnlyCanReadButNotWrite() {
        String id = createPatient("Read", "Only");

        assertThat(api.get("/api/v1/patients/" + id, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/patients", readOnly, patientBody("Nope", "Denied"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void searchFindsByNameEmailAndPhone() {
        createPatient("Zelda", "Fitzgerald");

        for (String q : List.of("zelda", "Fitz", "zelda.fitzgerald@example.test", "867-5309")) {
            ResponseEntity<Map<String, Object>> result =
                    api.get("/api/v1/patients?q=" + q, frontDesk);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((List<?>) result.getBody().get("content"))
                    .as("search for %s", q)
                    .isNotEmpty();
        }
    }

    @Test
    void statusLifecycleAndValidationWork() {
        String id = createPatient("Status", "Cycle");

        ResponseEntity<Map<String, Object>> archived = api.patch(
                "/api/v1/patients/" + id + "/status", frontDesk, Map.of("status", "ARCHIVED"));
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archived.getBody().get("status")).isEqualTo("ARCHIVED");

        ResponseEntity<Map<String, Object>> invalid = api.patch(
                "/api/v1/patients/" + id + "/status", frontDesk, Map.of("status", "DECEASED"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void medicalAlertsCrud() {
        String id = createPatient("Allergy", "Prone");

        ResponseEntity<Map<String, Object>> created = api.post(
                "/api/v1/patients/" + id + "/alerts", frontDesk,
                Map.of("type", "ALLERGY", "description", "Penicillin", "severity", "HIGH"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String alertId = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/patients/" + id + "/alerts/" + alertId, frontDesk,
                Map.of("type", "ALLERGY", "description", "Penicillin (anaphylaxis)",
                        "severity", "HIGH", "active", true));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<Map<String, Object>>> list =
                api.getList("/api/v1/patients/" + id + "/alerts", frontDesk);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).get("description")).isEqualTo("Penicillin (anaphylaxis)");

        assertThat(api.delete("/api/v1/patients/" + id + "/alerts/" + alertId, frontDesk)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.getList("/api/v1/patients/" + id + "/alerts", frontDesk).getBody())
                .isEmpty();
    }

    @Test
    void familyLinksAreBidirectional() {
        String parent = createPatient("Pat", "Parent");
        String child = createPatient("Kid", "Parent");

        ResponseEntity<Map<String, Object>> link = api.post(
                "/api/v1/patients/" + parent + "/family", frontDesk,
                Map.of("relatedPatientId", child, "relationship", "CHILD"));
        assertThat(link.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List<Map<String, Object>>> childSide =
                api.getList("/api/v1/patients/" + child + "/family", frontDesk);
        assertThat(childSide.getBody()).hasSize(1);
        assertThat(childSide.getBody().get(0).get("relationship")).isEqualTo("PARENT");

        // duplicate link rejected
        assertThat(api.post("/api/v1/patients/" + parent + "/family", frontDesk,
                Map.of("relatedPatientId", child, "relationship", "CHILD"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // removing one side removes both
        String linkId = (String) link.getBody().get("id");
        assertThat(api.delete("/api/v1/patients/" + parent + "/family/" + linkId, frontDesk)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.getList("/api/v1/patients/" + child + "/family", frontDesk).getBody())
                .isEmpty();
    }

    @Test
    void timelineRecordsLifecycleEvents() {
        String id = createPatient("Time", "Line");
        api.patch("/api/v1/patients/" + id + "/status", frontDesk, Map.of("status", "INACTIVE"));

        ResponseEntity<List<Map<String, Object>>> timeline =
                api.getList("/api/v1/patients/" + id + "/timeline", frontDesk);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline.getBody().stream().map(e -> e.get("action")))
                .contains("CREATE", "STATUS_CHANGE");
    }

    @Test
    void softDeleteHidesPatientAndRequiresAdmin() {
        String id = createPatient("Soft", "Delete");

        assertThat(api.delete("/api/v1/patients/" + id, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.delete("/api/v1/patients/" + id, admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.get("/api/v1/patients/" + id, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidDemographics() {
        Map<String, Object> body = patientBody("Bad", "Data");
        body.put("dateOfBirth", "2999-01-01"); // future
        assertThat(api.post("/api/v1/patients", frontDesk, body).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> noName = patientBody("", "Data");
        assertThat(api.post("/api/v1/patients", frontDesk, noName).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
