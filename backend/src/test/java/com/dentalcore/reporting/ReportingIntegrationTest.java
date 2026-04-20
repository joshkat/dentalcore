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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-reports@clinic.test";
    private static final String FRONT_EMAIL = "front-reports@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(5_500_000_000L);

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
    private HttpHeaders frontDesk;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private <T> ResponseEntity<List<T>> getList(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void appointmentsByProviderAndUtilizationCountBookedWork() {
        String patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Rep", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1991-01-01", "sex", "MALE")).getBody().get("id");
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Reporty", "lastName", "Prov" + SEQ.get(),
                "npi", String.valueOf(SEQ.get()))).getBody().get("id");
        String operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Report Op " + SEQ.get())).getBody().get("id");

        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(SEQ.get() % 500 + 3000, ChronoUnit.DAYS);
        // one completed (60 min), one left scheduled (30 min)
        String firstId = (String) api.post("/api/v1/appointments", admin, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(60, ChronoUnit.MINUTES).toString())).getBody().get("id");
        api.post("/api/v1/appointments", admin, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.plus(2, ChronoUnit.HOURS).toString(),
                "endsAt", start.plus(150, ChronoUnit.MINUTES).toString()));
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + firstId + "/status", admin,
                    Map.of("status", status));
        }

        LocalDate from = LocalDate.ofInstant(start.minus(1, ChronoUnit.DAYS),
                java.time.ZoneOffset.UTC);
        LocalDate to = from.plusDays(3);

        ResponseEntity<List<Map<String, Object>>> byProvider = getList(
                "/api/v1/reports/appointments-by-provider?from=" + from + "&to=" + to, admin);
        assertThat(byProvider.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = byProvider.getBody().stream()
                .filter(r -> providerId.equals(r.get("providerId")))
                .findFirst().orElseThrow();
        assertThat(((Number) row.get("completed")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("scheduled")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("total")).longValue()).isEqualTo(2);

        ResponseEntity<List<Map<String, Object>>> utilization = getList(
                "/api/v1/reports/provider-utilization?from=" + from + "&to=" + to, admin);
        Map<String, Object> util = utilization.getBody().stream()
                .filter(r -> providerId.equals(r.get("providerId")))
                .findFirst().orElseThrow();
        assertThat(((Number) util.get("bookedMinutes")).longValue()).isEqualTo(90);
        assertThat(((Number) util.get("completedMinutes")).longValue()).isEqualTo(60);
        assertThat(((Number) util.get("distinctPatients")).longValue()).isEqualTo(1);
    }

    @Test
    void dailyProductionAggregatesLedgerByDay() {
        String patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Prod", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1993-03-03", "sex", "FEMALE")).getBody().get("id");
        api.post("/api/v1/billing/charges", admin, Map.of(
                "patientId", patientId, "amount", 300, "description", "Report charge"));
        api.post("/api/v1/billing/payments", admin, Map.of(
                "patientId", patientId, "amount", 100, "method", "CASH"));

        LocalDate today = LocalDate.now();
        ResponseEntity<Map<String, Object>> report = api.get(
                "/api/v1/reports/daily-production?from=" + today + "&to=" + today, admin);
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);

        // other tests post to today's ledger too, so assert at-least semantics
        assertThat(((Number) report.getBody().get("totalCharges")).doubleValue())
                .isGreaterThanOrEqualTo(300.0);
        assertThat(((Number) report.getBody().get("totalPatientPayments")).doubleValue())
                .isGreaterThanOrEqualTo(100.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) report.getBody().get("days");
        assertThat(days).anyMatch(d -> today.toString().equals(d.get("date")));
    }

    @Test
    void patientGrowthCountsThisMonth() {
        api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Growth", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1994-04-04", "sex", "MALE"));

        ResponseEntity<List<Map<String, Object>>> growth =
                getList("/api/v1/reports/patient-growth?months=3", admin);
        assertThat(growth.getStatusCode()).isEqualTo(HttpStatus.OK);
        String thisMonth = LocalDate.now().toString().substring(0, 7);
        Map<String, Object> current = growth.getBody().stream()
                .filter(r -> thisMonth.equals(r.get("month")))
                .findFirst().orElseThrow();
        assertThat(((Number) current.get("newPatients")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) current.get("cumulative")).longValue())
                .isGreaterThanOrEqualTo(((Number) current.get("newPatients")).longValue());
    }

    @Test
    void dashboardSummaryAndRbacWork() {
        ResponseEntity<Map<String, Object>> dashboard =
                api.get("/api/v1/reports/dashboard", frontDesk);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) dashboard.getBody().get("activePatients")).longValue())
                .isGreaterThanOrEqualTo(1);

        // financial report is for ADMIN/BILLING only
        LocalDate today = LocalDate.now();
        assertThat(api.get("/api/v1/reports/daily-production?from=" + today + "&to=" + today,
                frontDesk).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // invalid range rejected
        assertThat(api.get("/api/v1/reports/provider-utilization?from=" + today
                + "&to=" + today.minusDays(2), admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
