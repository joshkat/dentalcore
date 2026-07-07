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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ToothChartIntegrationTest extends IntegrationTest {

    private static final String DENTIST_EMAIL = "dentist-chart@clinic.test";
    private static final String FRONT_EMAIL = "front-chart@clinic.test";
    private static final String ADMIN_EMAIL = "admin-chart@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong NPI_SEQ = new AtomicLong(7_700_000_000L);

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

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(ADMIN_EMAIL, "ADMIN");
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", dentist, Map.of(
                "firstName", "Chart", "lastName", "Patient",
                "dateOfBirth", "1985-05-05", "sex", "MALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private ResponseEntity<Map<String, Object>> addCondition(String tooth, String condition,
                                                             String surfaces) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("tooth", tooth);
        body.put("condition", condition);
        if (surfaces != null) {
            body.put("surfaces", surfaces);
        }
        return api.post("/api/v1/patients/" + patientId + "/tooth-conditions", dentist, body);
    }

    @Test
    void conditionsAreChartedNormalizedAndResolvable() {
        ResponseEntity<Map<String, Object>> created = addCondition("26", "CARIES", "dom");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("surfaces")).isEqualTo("MOD");
        String conditionId = (String) created.getBody().get("id");

        ResponseEntity<Map<String, Object>> resolved = api.post(
                "/api/v1/patients/" + patientId + "/tooth-conditions/" + conditionId + "/resolve",
                dentist, null);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().get("status")).isEqualTo("RESOLVED");
        assertThat(resolved.getBody().get("resolvedAt")).isNotNull();

        // resolved history stays on the chart
        ResponseEntity<Map<String, Object>> chart =
                api.get("/api/v1/patients/" + patientId + "/chart", dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions =
                (List<Map<String, Object>>) chart.getBody().get("conditions");
        assertThat(conditions).hasSize(1);
    }

    @Test
    void missingToothRulesAreEnforced() {
        addCondition("36", "CARIES", "O");

        // cannot mark missing while another condition is active
        assertThat(addCondition("36", "MISSING", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // resolve, then missing works
        ResponseEntity<Map<String, Object>> chart =
                api.get("/api/v1/patients/" + patientId + "/chart", dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions =
                (List<Map<String, Object>>) chart.getBody().get("conditions");
        String cariesId = (String) conditions.get(0).get("id");
        api.post("/api/v1/patients/" + patientId + "/tooth-conditions/" + cariesId + "/resolve",
                dentist, null);
        assertThat(addCondition("36", "MISSING", null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // nothing can be charted on a missing tooth
        assertThat(addCondition("36", "CROWN", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidToothAndSurfacesAreRejected() {
        assertThat(addCondition("49", "CARIES", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(addCondition("26", "CARIES", "XY").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void chartIncludesTreatmentPlanProcedures() {
        HttpHeaders admin = api.login(ADMIN_EMAIL, PASSWORD);
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Chart", "lastName", "Prov",
                "npi", String.valueOf(NPI_SEQ.incrementAndGet()))).getBody().get("id");
        String planId = (String) api.post("/api/v1/treatment-plans", dentist, Map.of(
                "patientId", patientId, "providerId", providerId, "title", "Crown 26"))
                .getBody().get("id");
        ResponseEntity<Map<String, Object>> catalog =
                api.get("/api/v1/procedure-codes?q=D2740", dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes =
                (List<Map<String, Object>>) catalog.getBody().get("content");
        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", codes.get(0).get("id"), "tooth", "26", "surface", "MOD"));

        ResponseEntity<Map<String, Object>> chart =
                api.get("/api/v1/patients/" + patientId + "/chart", dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) chart.getBody().get("procedures");
        assertThat(procedures).hasSize(1);
        assertThat(procedures.get(0).get("tooth")).isEqualTo("26");
        assertThat(procedures.get(0).get("code")).isEqualTo("D2740");
        assertThat(procedures.get(0).get("planTitle")).isEqualTo("Crown 26");
    }

    @Test
    void frontDeskCanViewButNotChart() {
        assertThat(api.get("/api/v1/patients/" + patientId + "/chart", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/patients/" + patientId + "/tooth-conditions", frontDesk,
                Map.of("tooth", "11", "condition", "WATCH")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void profileExtrasAndRecallRoundTrip() {
        HttpHeaders admin = api.login(ADMIN_EMAIL, PASSWORD);
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Primary", "lastName", "Prov",
                "npi", String.valueOf(NPI_SEQ.incrementAndGet()))).getBody().get("id");

        Map<String, Object> update = new java.util.HashMap<>();
        update.put("firstName", "Chart");
        update.put("lastName", "Patient");
        update.put("dateOfBirth", "1985-05-05");
        update.put("sex", "MALE");
        update.put("preferredName", "Chuck");
        update.put("pronouns", "he/him");
        update.put("employer", "Acme Corp");
        update.put("referralSource", "Google");
        update.put("preferredContactMethod", "SMS");
        update.put("smsConsent", true);
        update.put("pharmacyName", "Main St Pharmacy");
        update.put("primaryProviderId", providerId);
        update.put("smokingStatus", "FORMER");

        ResponseEntity<Map<String, Object>> updated =
                api.put("/api/v1/patients/" + patientId, dentist, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("preferredName")).isEqualTo("Chuck");
        assertThat(updated.getBody().get("smsConsent")).isEqualTo(true);
        assertThat(updated.getBody().get("smokingStatus")).isEqualTo("FORMER");
        assertThat(updated.getBody().get("primaryProviderLastName")).isEqualTo("Prov");

        // recall
        ResponseEntity<Map<String, Object>> recall = api.patch(
                "/api/v1/patients/" + patientId + "/recall", dentist,
                Map.of("intervalMonths", 6, "nextRecallDate", "2026-12-01"));
        assertThat(recall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recall.getBody().get("nextRecallDate")).isEqualTo("2026-12-01");

        // unknown primary provider rejected
        update.put("primaryProviderId", "00000000-0000-0000-0000-00000000dead");
        assertThat(api.put("/api/v1/patients/" + patientId, dentist, update).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // MEDICATION alert type works
        assertThat(api.post("/api/v1/patients/" + patientId + "/alerts", dentist,
                Map.of("type", "MEDICATION", "description", "Lisinopril 10mg daily",
                        "severity", "LOW")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
}
