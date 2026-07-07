package com.dentalcore.procedures;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CompletedProcedureIntegrationTest extends IntegrationTest {

    private static final String DENTIST_EMAIL = "dentist-completed@clinic.test";
    private static final String BILLING_EMAIL = "billing-completed@clinic.test";
    private static final String ADMIN_EMAIL = "admin-completed@clinic.test";
    private static final String READONLY_EMAIL = "readonly-completed@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (7_7 belongs to ToothChartIntegrationTest)
    private static final AtomicLong SEQ = new AtomicLong(2_200_000_000L);

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
    private HttpHeaders billing;
    private HttpHeaders admin;
    private HttpHeaders readOnly;
    private String patientId;
    private String providerId;
    private String operatoryId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        billing = api.login(BILLING_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", dentist, Map.of(
                "firstName", "Completed", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1985-05-05", "sex", "MALE")).getBody().get("id");
        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Done", "lastName", "Dentist",
                "npi", String.valueOf(SEQ.incrementAndGet()))).getBody().get("id");
        operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Completed Op " + SEQ.get())).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String findCodeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, dentist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }

    private double balance() {
        return ((Number) api.get("/api/v1/billing/balance?patientId=" + patientId, billing)
                .getBody().get("balance")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ledgerEntries() {
        return (List<Map<String, Object>>) api
                .get("/api/v1/billing/ledger?patientId=" + patientId, billing)
                .getBody().get("content");
    }

    private List<Map<String, Object>> completedList() {
        return api.getList("/api/v1/completed-procedures?patientId=" + patientId, dentist)
                .getBody();
    }

    private String bookAppointment(List<String> procedureCodeIds) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(SEQ.incrementAndGet() % 1000 + 4000, ChronoUnit.DAYS);
        String appointmentId = (String) api.post("/api/v1/appointments", dentist, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(1, ChronoUnit.HOURS).toString())).getBody().get("id");
        if (!procedureCodeIds.isEmpty()) {
            api.put("/api/v1/appointments/" + appointmentId + "/procedures", dentist,
                    Map.of("procedureCodeIds", procedureCodeIds));
        }
        return appointmentId;
    }

    /** DRAFT -> PRESENTED -> APPROVED plan with one procedure; returns [planId, plannedProcedureId]. */
    private String[] approvedPlanWithProcedure(String codeId, String tooth, String surface) {
        String planId = (String) api.post("/api/v1/treatment-plans", dentist, Map.of(
                "patientId", patientId, "providerId", providerId, "title", "Phase A"))
                .getBody().get("id");
        Map<String, Object> body = new HashMap<>();
        body.put("procedureCodeId", codeId);
        if (tooth != null) {
            body.put("tooth", tooth);
        }
        if (surface != null) {
            body.put("surface", surface);
        }
        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist, body);
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "PRESENTED"));
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "APPROVED"));
        return new String[]{planId, planProcedure(planId, "id")};
    }

    private String planProcedure(String planId, String field) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures = (List<Map<String, Object>>) api
                .get("/api/v1/treatment-plans/" + planId, dentist)
                .getBody().get("procedures");
        return String.valueOf(procedures.get(0).get(field));
    }

    @Test
    void completePostsChargeFlipsPlanAndPaintsChart() {
        String d2391 = findCodeId("D2391"); // RESTORATIVE, 205.00
        String[] plan = approvedPlanWithProcedure(d2391, "30", "O");

        ResponseEntity<Map<String, Object>> response = api.post(
                "/api/v1/completed-procedures", dentist, Map.of(
                        "patientId", patientId, "providerId", providerId,
                        "procedureCodeId", d2391, "plannedProcedureId", plan[1],
                        "tooth", "46", "surfaces", "O"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("code")).isEqualTo("D2391");
        assertThat(((Number) response.getBody().get("fee")).doubleValue()).isEqualTo(205.0);
        assertThat(response.getBody().get("tooth")).isEqualTo("46");
        assertThat(response.getBody().get("plannedProcedureId")).isEqualTo(plan[1]);
        assertThat(response.getBody().get("providerLastName")).isEqualTo("Dentist");
        assertThat(response.getBody().get("entryDate")).isNotNull();

        // the ledger charge exists with the matching amount and format
        assertThat(balance()).isEqualTo(205.0);
        Map<String, Object> charge = ledgerEntries().get(0);
        assertThat(charge.get("type")).isEqualTo("CHARGE");
        assertThat(((Number) charge.get("amount")).doubleValue()).isEqualTo(205.0);
        assertThat(charge.get("description"))
                .isEqualTo("D2391 - Resin-based composite - one surface, posterior #46");

        // the planned procedure flipped to COMPLETED
        assertThat(planProcedure(plan[0], "status")).isEqualTo("COMPLETED");

        // the chart got painted with a RESTORATION on tooth 30
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) api
                .get("/api/v1/patients/" + patientId + "/chart", dentist)
                .getBody().get("conditions");
        assertThat(conditions).anyMatch(c -> "RESTORATION".equals(c.get("condition"))
                && "46".equals(c.get("tooth"))
                && "Completed D2391".equals(c.get("notes")));

        assertThat(completedList()).hasSize(1);
    }

    @Test
    void checkoutThenCompleteNeverDoubleCharges() {
        String d1110 = findCodeId("D1110"); // 120.00
        String d0120 = findCodeId("D0120"); // 65.00
        String appointmentId = bookAppointment(List.of(d1110, d0120));
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS")) {
            api.patch("/api/v1/appointments/" + appointmentId + "/status", dentist,
                    Map.of("status", status));
        }

        // checkout completes one of the two procedures ahead of time
        ResponseEntity<Map<String, Object>> early = api.post(
                "/api/v1/completed-procedures", dentist, Map.of(
                        "patientId", patientId, "providerId", providerId,
                        "procedureCodeId", d1110, "appointmentId", appointmentId));
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balance()).isEqualTo(120.0);

        // completing the appointment only charges the remaining procedure
        assertThat(api.patch("/api/v1/appointments/" + appointmentId + "/status", dentist,
                Map.of("status", "COMPLETED")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(balance()).isEqualTo(185.0);
        List<Map<String, Object>> entries = ledgerEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> "CHARGE".equals(e.get("type"))
                && appointmentId.equals(e.get("appointmentId")));
        assertThat(completedList()).hasSize(2);
    }

    @Test
    void plainCompletionStillChargesEveryProcedure() {
        String d1110 = findCodeId("D1110");
        String d0120 = findCodeId("D0120");
        String appointmentId = bookAppointment(List.of(d1110, d0120));
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + appointmentId + "/status", dentist,
                    Map.of("status", status));
        }

        assertThat(balance()).isEqualTo(185.0);
        assertThat(ledgerEntries()).hasSize(2);
        List<Map<String, Object>> completed = completedList();
        assertThat(completed).hasSize(2);
        assertThat(completed).allMatch(c -> appointmentId.equals(c.get("appointmentId")));
    }

    @Test
    void sameDayUndoReversesChargeAndRevertsPlan() {
        String d2740 = findCodeId("D2740"); // 1250.00
        String[] plan = approvedPlanWithProcedure(d2740, "14", null);

        String completedId = (String) api.post("/api/v1/completed-procedures", dentist, Map.of(
                "patientId", patientId, "providerId", providerId,
                "procedureCodeId", d2740, "plannedProcedureId", plan[1], "tooth", "26"))
                .getBody().get("id");
        assertThat(balance()).isEqualTo(1250.0);
        assertThat(planProcedure(plan[0], "status")).isEqualTo("COMPLETED");

        ResponseEntity<Map<String, Object>> undo = api.delete(
                "/api/v1/completed-procedures/" + completedId, dentist);
        assertThat(undo.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // reversal entry exists and the balance nets to zero
        assertThat(balance()).isEqualTo(0.0);
        List<Map<String, Object>> entries = ledgerEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries).anyMatch(e -> "ADJUSTMENT".equals(e.get("type"))
                && e.get("reversalOf") != null
                && ((Number) e.get("amount")).doubleValue() == -1250.0);

        // the planned procedure went back to PLANNED and the row is gone
        assertThat(planProcedure(plan[0], "status")).isEqualTo("PLANNED");
        assertThat(completedList()).isEmpty();
    }

    @Test
    void rbacIsEnforced() {
        String d0120 = findCodeId("D0120");
        // READ_ONLY may neither complete nor undo
        assertThat(api.post("/api/v1/completed-procedures", readOnly, Map.of(
                "patientId", patientId, "providerId", providerId,
                "procedureCodeId", d0120)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.delete("/api/v1/completed-procedures/" + patientId, readOnly)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // but any authenticated role may read the history
        assertThat(api.getList("/api/v1/completed-procedures?patientId=" + patientId, readOnly)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
