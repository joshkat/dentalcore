package com.dentalcore.reminders;

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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-remind@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(3_300_000_000L);

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

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        if (userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).isEmpty()) {
            User user = new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "A", "A", null);
            user.setRoles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()));
            userRepository.save(user);
        }
        admin = api.login(ADMIN_EMAIL, PASSWORD);
    }

    private String createPatient(Map<String, Object> extra) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "firstName", "Remind", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-02-02", "sex", "FEMALE"));
        body.putAll(extra);
        return (String) api.post("/api/v1/patients", admin, body).getBody().get("id");
    }

    @Test
    void appointmentReminderSentOnceAndConsentRespected() {
        // consented patient with phone, SMS preferred
        String consented = createPatient(Map.of(
                "smsConsent", true, "preferredContactMethod", "SMS",
                "phones", List.of(Map.of("type", "MOBILE", "number", "555-444-0001",
                        "primary", true))));
        // no consent at all
        String unconsented = createPatient(Map.of(
                "email", "no-consent@example.test"));

        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "R", "lastName", "Prov" + SEQ.get(),
                "npi", String.valueOf(SEQ.get()))).getBody().get("id");
        String operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Remind Op " + SEQ.get())).getBody().get("id");

        // both have appointments tomorrow (clinic timezone)
        ZonedDateTime tomorrow = ZonedDateTime.now(ZoneId.of("America/New_York"))
                .toLocalDate().plusDays(1).atTime(9, 0)
                .atZone(ZoneId.of("America/New_York"));
        for (var entry : List.of(Map.entry(consented, 9), Map.entry(unconsented, 11))) {
            assertThat(api.post("/api/v1/appointments", admin, Map.of(
                    "patientId", entry.getKey(), "providerId", providerId,
                    "operatoryId", operatoryId,
                    "startsAt", tomorrow.withHour(entry.getValue()).toInstant().toString(),
                    "endsAt", tomorrow.withHour(entry.getValue()).plusMinutes(30)
                            .toInstant().toString())).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<Map<String, Object>> first =
                api.post("/api/v1/reminders/run", admin, null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        int sent = ((Number) first.getBody().get("appointmentSent")).intValue();
        int skipped = ((Number) first.getBody().get("appointmentSkipped")).intValue();
        assertThat(sent).isGreaterThanOrEqualTo(1);
        assertThat(skipped).isGreaterThanOrEqualTo(1); // unconsented patient

        // second run: nothing new for those appointments (dedupe)
        ResponseEntity<Map<String, Object>> second =
                api.post("/api/v1/reminders/run", admin, null);
        assertThat(((Number) second.getBody().get("appointmentSent")).intValue()).isZero();
        assertThat(((Number) second.getBody().get("appointmentSkipped")).intValue()).isZero();
    }

    @Test
    void recallWorklistAndRecallRemindersWork() {
        String due = createPatient(Map.of(
                "email", "recall-due@example.test", "emailConsent", true));
        api.patch("/api/v1/patients/" + due + "/recall", admin,
                Map.of("intervalMonths", 6, "nextRecallDate", LocalDate.now().minusDays(3)
                        .toString()));

        ResponseEntity<java.util.List<Map<String, Object>>> worklist =
                api.getList("/api/v1/reminders/recall-worklist?daysAhead=14", admin);
        assertThat(worklist.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(worklist.getBody()).anyMatch(r -> due.equals(r.get("patientId")));

        ResponseEntity<Map<String, Object>> run = api.post("/api/v1/reminders/run", admin, null);
        assertThat(((Number) run.getBody().get("recallSent")).intValue())
                .isGreaterThanOrEqualTo(1);

        // cooldown: immediate rerun does not resend to the same patient
        ResponseEntity<java.util.List<Map<String, Object>>> after =
                api.getList("/api/v1/reminders/recall-worklist?daysAhead=14", admin);
        Map<String, Object> row = after.getBody().stream()
                .filter(r -> due.equals(r.get("patientId"))).findFirst().orElseThrow();
        assertThat(row.get("lastReminderAt")).isNotNull();
    }

    @Test
    void completedHygieneVisitBumpsRecall() {
        String patient = createPatient(Map.of());
        String hygienistId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "HYGIENIST", "firstName", "Bump", "lastName", "Hyg" + SEQ.incrementAndGet(),
                "npi", String.valueOf(SEQ.get()))).getBody().get("id");
        String operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Bump Op " + SEQ.get())).getBody().get("id");

        java.time.Instant start = java.time.Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .plus(SEQ.get() % 200 + 4000, java.time.temporal.ChronoUnit.DAYS);
        String appointmentId = (String) api.post("/api/v1/appointments", admin, Map.of(
                "patientId", patient, "providerId", hygienistId, "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(30, java.time.temporal.ChronoUnit.MINUTES).toString()))
                .getBody().get("id");
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + appointmentId + "/status", admin,
                    Map.of("status", status));
        }

        ResponseEntity<Map<String, Object>> after =
                api.get("/api/v1/patients/" + patient, admin);
        String nextRecall = (String) after.getBody().get("nextRecallDate");
        assertThat(nextRecall).isNotNull();
        assertThat(LocalDate.parse(nextRecall))
                .isEqualTo(LocalDate.now(ZoneId.of("America/New_York")).plusMonths(6));
    }
}
