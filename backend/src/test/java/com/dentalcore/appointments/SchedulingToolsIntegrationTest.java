package com.dentalcore.appointments;

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

class SchedulingToolsIntegrationTest extends IntegrationTest {

    private static final String FRONT_EMAIL = "front-sched-tools@clinic.test";
    private static final String READONLY_EMAIL = "readonly-sched-tools@clinic.test";
    private static final String ADMIN_EMAIL = "admin-sched-tools@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong NPI_SEQ = new AtomicLong(4_500_000_000L);
    private static final AtomicLong DAY_SEQ = new AtomicLong(3000);

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
    private String patientId;
    private String providerId;
    private String operatoryId;
    private Instant slotStart;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        seedUser(ADMIN_EMAIL, "ADMIN");
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Tool", "lastName", "Patient-" + DAY_SEQ.get(),
                "dateOfBirth", "1980-01-01", "sex", "FEMALE")).getBody().get("id");
        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Series", "lastName", "Dentist",
                "npi", String.valueOf(NPI_SEQ.incrementAndGet()),
                "color", "#0ea5e9")).getBody().get("id");
        operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Tools Op " + NPI_SEQ.get())).getBody().get("id");
        // fresh provider has no working-hours template, so any far-future slot books
        slotStart = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(DAY_SEQ.incrementAndGet(), ChronoUnit.DAYS);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private Map<String, Object> appointmentBody(Instant start, int minutes) {
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patientId);
        body.put("providerId", providerId);
        body.put("operatoryId", operatoryId);
        body.put("startsAt", start.toString());
        body.put("endsAt", start.plus(minutes, ChronoUnit.MINUTES).toString());
        return body;
    }

    private Map<String, Object> blockoutBody(Instant start, Instant end, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("operatoryId", operatoryId);
        body.put("startsAt", start.toString());
        body.put("endsAt", end.toString());
        body.put("reason", reason);
        return body;
    }

    @Test
    void blockoutPreventsBookingThatOperatory() {
        // block the whole hour around the slot
        api.post("/api/v1/blockouts", admin,
                blockoutBody(slotStart.minus(30, ChronoUnit.MINUTES),
                        slotStart.plus(60, ChronoUnit.MINUTES), "Maintenance"));

        ResponseEntity<Map<String, Object>> blocked =
                api.post("/api/v1/appointments", frontDesk, appointmentBody(slotStart, 30));
        assertThat(blocked.getStatusCode())
                .as("booking into a blocked operatory: %s", blocked.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // a slot well after the blockout still books
        ResponseEntity<Map<String, Object>> ok = api.post("/api/v1/appointments", frontDesk,
                appointmentBody(slotStart.plus(3, ChronoUnit.HOURS), 30));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void blockoutListsInRangeAndDeletes() {
        String id = (String) api.post("/api/v1/blockouts", admin,
                blockoutBody(slotStart, slotStart.plus(60, ChronoUnit.MINUTES), "Lunch"))
                .getBody().get("id");

        ResponseEntity<List<Map<String, Object>>> listed = api.getList(
                "/api/v1/blockouts?from=" + slotStart.minus(1, ChronoUnit.DAYS)
                        + "&to=" + slotStart.plus(1, ChronoUnit.DAYS), admin);
        assertThat(listed.getBody()).anyMatch(b -> id.equals(b.get("id")));

        assertThat(api.delete("/api/v1/blockouts/" + id, admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void readOnlyCannotCreateBlockout() {
        assertThat(api.post("/api/v1/blockouts", readOnly,
                blockoutBody(slotStart, slotStart.plus(30, ChronoUnit.MINUTES), "x"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recurringCreatesSeriesAndSkipsConflicts() {
        // pre-book the week-2 occurrence's exact slot so it must be skipped
        Instant week2 = slotStart.plus(14, ChronoUnit.DAYS);
        assertThat(api.post("/api/v1/appointments", frontDesk, appointmentBody(week2, 30))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = new HashMap<>();
        body.put("base", appointmentBody(slotStart, 30));
        body.put("frequency", "WEEKLY");
        body.put("occurrences", 4);

        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/appointments/recurring", frontDesk, body);
        assertThat(response.getStatusCode())
                .as("recurring create: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> result = response.getBody();
        List<Map<String, Object>> created = (List<Map<String, Object>>) result.get("created");
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(created).hasSize(3);
        assertThat(skipped).hasSize(1);
        // every created occurrence carries the same series id
        Object seriesId = result.get("seriesId");
        assertThat(seriesId).isNotNull();
        assertThat(created).allMatch(a -> seriesId.equals(a.get("seriesId")));
    }

    @Test
    void sendConfirmationStampsTimestamp() {
        String id = (String) api.post("/api/v1/appointments", frontDesk,
                appointmentBody(slotStart, 30)).getBody().get("id");
        assertThat(api.get("/api/v1/appointments/" + id, frontDesk).getBody()
                .get("confirmationSentAt")).isNull();

        ResponseEntity<Map<String, Object>> confirmed =
                api.post("/api/v1/appointments/" + id + "/send-confirmation", frontDesk, null);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().get("confirmationSentAt")).isNotNull();
    }
}
