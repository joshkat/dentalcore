package com.dentalcore.treatmentplans;

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

class TreatmentPlanIntegrationTest extends IntegrationTest {

    private static final String DENTIST_EMAIL = "dentist-plans@clinic.test";
    private static final String FRONT_EMAIL = "front-plans@clinic.test";
    private static final String ADMIN_EMAIL = "admin-plans@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong NPI_SEQ = new AtomicLong(8_800_000_000L);

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
    private String patientId;
    private String providerId;
    private String prophyCodeId; // D1110, $120
    private String crownCodeId;  // D2740, $1250

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(ADMIN_EMAIL, "ADMIN");
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        HttpHeaders admin = api.login(ADMIN_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", dentist, Map.of(
                "firstName", "Plan", "lastName", "Patient",
                "dateOfBirth", "1980-01-01", "sex", "FEMALE")).getBody().get("id");
        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Plan", "lastName", "Dentist",
                "npi", String.valueOf(NPI_SEQ.incrementAndGet()))).getBody().get("id");

        prophyCodeId = findCodeId("D1110");
        crownCodeId = findCodeId("D2740");
    }

    private String findCodeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String createPlan() {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/treatment-plans", dentist,
                Map.of("patientId", patientId, "providerId", providerId, "title", "Phase I"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void procedureFeeDefaultsFromCatalogAndTotalsAdd() {
        String planId = createPlan();

        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", prophyCodeId));
        ResponseEntity<Map<String, Object>> withCrown = api.post(
                "/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", crownCodeId, "tooth", "14", "surface", "MOD",
                        "priority", 2));

        assertThat(withCrown.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(withCrown.getBody().get("totalEstimatedCost")).isEqualTo(1370.0);
        assertThat(withCrown.getBody().get("procedureCount")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) withCrown.getBody().get("procedures");
        assertThat(procedures.get(1).get("code")).isEqualTo("D2740");
        assertThat(procedures.get(1).get("estimatedCost")).isEqualTo(1250.0);
    }

    @Test
    void approvalIsTrackedWithUserAndTimestamp() {
        String planId = createPlan();
        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", prophyCodeId));

        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "PRESENTED"));
        ResponseEntity<Map<String, Object>> approved = api.patch(
                "/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "APPROVED"));

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("approvedAt")).isNotNull();
        assertThat(approved.getBody().get("approvedBy")).isNotNull();
    }

    @Test
    void lifecycleSkipsAreRejected() {
        String planId = createPlan();
        assertThat(api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "COMPLETED")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void procedureEditsLockAfterApproval() {
        String planId = createPlan();
        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", prophyCodeId));
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "PRESENTED"));
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "APPROVED"));

        assertThat(api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", crownCodeId)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void completingProceduresTracksProgress() {
        String planId = createPlan();
        ResponseEntity<Map<String, Object>> added = api.post(
                "/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", prophyCodeId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) added.getBody().get("procedures");
        String procedureId = (String) procedures.get(0).get("id");

        for (String status : List.of("PRESENTED", "APPROVED", "IN_PROGRESS")) {
            api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                    Map.of("status", status));
        }

        ResponseEntity<Map<String, Object>> completed = api.patch(
                "/api/v1/treatment-plans/" + planId + "/procedures/" + procedureId + "/status",
                dentist, Map.of("status", "COMPLETED"));
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("completedCount")).isEqualTo(1);
        assertThat(completed.getBody().get("completedCost")).isEqualTo(120.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after =
                (List<Map<String, Object>>) completed.getBody().get("procedures");
        assertThat(after.get(0).get("completedAt")).isNotNull();
    }

    @Test
    void frontDeskCanReadButNotWritePlans() {
        String planId = createPlan();
        assertThat(api.get("/api/v1/treatment-plans/" + planId, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/treatment-plans", frontDesk,
                Map.of("patientId", patientId, "providerId", providerId, "title", "Nope"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
