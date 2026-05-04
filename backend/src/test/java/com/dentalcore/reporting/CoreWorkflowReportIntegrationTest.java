package com.dentalcore.reporting;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CoreWorkflowReportIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-workflow@clinic.test";
    private static final String DENTIST_EMAIL = "dentist-workflow@clinic.test";
    private static final String FRONT_EMAIL = "front-workflow@clinic.test";
    private static final String READONLY_EMAIL = "readonly-workflow@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (see AppointmentIntegrationTest 9_9 / ToothChart 7_7)
    private static final AtomicLong SEQ = new AtomicLong(2_100_000_000L);

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
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private String providerId;
    private String operatoryId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(DENTIST_EMAIL, "DENTIST");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Work", "lastName", "Flow",
                "npi", String.valueOf(SEQ.incrementAndGet()))).getBody().get("id");
        operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Workflow Op " + SEQ.get())).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String newPatient() {
        return (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Work", "lastName", "Flow" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-09-09", "sex", "FEMALE",
                "phones", List.of(Map.of(
                        "type", "MOBILE", "number", "555-0100", "primary", true))))
                .getBody().get("id");
    }

    private String bookAppointment(String patientId, boolean asap) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(SEQ.incrementAndGet() % 1000 + 5000, ChronoUnit.DAYS);
        Map<String, Object> body = Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(1, ChronoUnit.HOURS).toString(),
                "asap", asap);
        return (String) api.post("/api/v1/appointments", frontDesk, body).getBody().get("id");
    }

    private String findCodeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, frontDesk);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }

    private Map<String, Object> daySheet() {
        return api.get("/api/v1/reports/day-sheet", admin).getBody();
    }

    private double total(Map<String, Object> daySheet, String field) {
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) daySheet.get("totals");
        return ((Number) totals.get(field)).doubleValue();
    }

    private double cashDeposit(Map<String, Object> daySheet, String field) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slip =
                (List<Map<String, Object>>) daySheet.get("depositSlip");
        return slip.stream()
                .filter(row -> "CASH".equals(row.get("method")))
                .findFirst()
                .map(row -> ((Number) row.get(field)).doubleValue())
                .orElse(0.0);
    }

    @Test
    void daySheetAddsUpProductionCollectionsAndDeposits() {
        String patientId = newPatient();
        // the suite shares one database, so assert on deltas around our postings
        Map<String, Object> before = daySheet();

        api.post("/api/v1/billing/charges", admin, Map.of(
                "patientId", patientId, "amount", 150, "description", "Day sheet charge"));
        api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", 100, "method", "CASH"));

        Map<String, Object> after = daySheet();
        assertThat(total(after, "production") - total(before, "production")).isEqualTo(150.0);
        assertThat(total(after, "collections") - total(before, "collections")).isEqualTo(100.0);
        assertThat(cashDeposit(after, "count") - cashDeposit(before, "count")).isEqualTo(1.0);
        assertThat(cashDeposit(after, "total") - cashDeposit(before, "total")).isEqualTo(100.0);

        // the manual charge has no completed procedure, so it shows unattributed
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) after.get("entries");
        Map<String, Object> charge = entries.stream()
                .filter(e -> patientId.equals(e.get("patientId"))
                        && "CHARGE".equals(e.get("type")))
                .findFirst().orElseThrow();
        assertThat(((Number) charge.get("amount")).doubleValue()).isEqualTo(150.0);
        assertThat(charge.get("providerName")).isNull();
        assertThat(entries).anyMatch(e -> patientId.equals(e.get("patientId"))
                && "PAYMENT".equals(e.get("type"))
                && ((Number) e.get("amount")).doubleValue() == -100.0);
        assertThat(after.get("date")).isNotNull();
    }

    @Test
    void unscheduledTreatmentListsPlannedWorkUntilBooked() {
        String patientId = newPatient();
        String planId = (String) api.post("/api/v1/treatment-plans", dentist, Map.of(
                "patientId", patientId, "providerId", providerId, "title", "Crown work"))
                .getBody().get("id");
        api.post("/api/v1/treatment-plans/" + planId + "/procedures", dentist,
                Map.of("procedureCodeId", findCodeId("D2740"))); // 1250.00
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "PRESENTED"));
        api.patch("/api/v1/treatment-plans/" + planId + "/status", dentist,
                Map.of("status", "APPROVED"));

        ResponseEntity<List<Map<String, Object>>> unscheduled =
                api.getList("/api/v1/reports/unscheduled-treatment", frontDesk);
        assertThat(unscheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = unscheduled.getBody().stream()
                .filter(r -> patientId.equals(r.get("patientId")))
                .findFirst().orElseThrow();
        assertThat(row.get("planId")).isEqualTo(planId);
        assertThat(row.get("planStatus")).isEqualTo("APPROVED");
        assertThat(((Number) row.get("plannedCount")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("remainingValue")).doubleValue()).isEqualTo(1250.0);
        assertThat(row.get("phone")).isEqualTo("555-0100");

        // booking a future appointment removes the patient from the list
        bookAppointment(patientId, false);
        assertThat(api.getList("/api/v1/reports/unscheduled-treatment", frontDesk).getBody())
                .noneMatch(r -> patientId.equals(r.get("patientId")));
    }

    @Test
    void asapFlagRoundTripsAndFeedsTheAsapList() {
        String patientId = newPatient();
        String appointmentId = bookAppointment(patientId, true);

        Map<String, Object> appointment =
                api.get("/api/v1/appointments/" + appointmentId, frontDesk).getBody();
        assertThat(appointment.get("asap")).isEqualTo(true);

        ResponseEntity<List<Map<String, Object>>> asapList =
                api.getList("/api/v1/reports/asap-list", frontDesk);
        assertThat(asapList.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = asapList.getBody().stream()
                .filter(r -> appointmentId.equals(r.get("appointmentId")))
                .findFirst().orElseThrow();
        assertThat(row.get("patientId")).isEqualTo(patientId);
        assertThat(row.get("status")).isEqualTo("SCHEDULED");
        assertThat(row.get("phone")).isEqualTo("555-0100");
        assertThat(row.get("providerName")).isEqualTo("Flow, Work");

        // clearing the flag on update drops it from the list
        api.put("/api/v1/appointments/" + appointmentId, frontDesk, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", appointment.get("startsAt"),
                "endsAt", appointment.get("endsAt"),
                "asap", false));
        assertThat(api.get("/api/v1/appointments/" + appointmentId, frontDesk)
                .getBody().get("asap")).isEqualTo(false);
        assertThat(api.getList("/api/v1/reports/asap-list", frontDesk).getBody())
                .noneMatch(r -> appointmentId.equals(r.get("appointmentId")));
    }

    @Test
    void walkoutReturnsAPdfForTheVisit() {
        String patientId = newPatient();
        String appointmentId = bookAppointment(patientId, false);
        api.put("/api/v1/appointments/" + appointmentId + "/procedures", dentist,
                Map.of("procedureCodeIds", List.of(findCodeId("D1110"))));
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + appointmentId + "/status", dentist,
                    Map.of("status", status));
        }
        api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", 50, "method", "CASH"));

        ResponseEntity<byte[]> pdf = rest.exchange(
                "/api/v1/billing/walkout?appointmentId=" + appointmentId,
                HttpMethod.GET, new HttpEntity<>(null, frontDesk), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");

        // unknown appointment is a 404
        assertThat(rest.exchange("/api/v1/billing/walkout?appointmentId=" + UUID.randomUUID(),
                HttpMethod.GET, new HttpEntity<>(null, frontDesk), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rbacIsEnforced() {
        // day sheet: billing-capable roles plus front desk
        assertThat(api.get("/api/v1/reports/day-sheet", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.get("/api/v1/reports/day-sheet", dentist).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/reports/day-sheet", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // worklists are for staff, not READ_ONLY
        assertThat(api.get("/api/v1/reports/unscheduled-treatment", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/reports/asap-list", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.getList("/api/v1/reports/asap-list", dentist).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
