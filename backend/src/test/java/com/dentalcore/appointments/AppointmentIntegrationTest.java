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

class AppointmentIntegrationTest extends IntegrationTest {

    private static final String FRONT_EMAIL = "front-appts@clinic.test";
    private static final String READONLY_EMAIL = "readonly-appts@clinic.test";
    private static final String ADMIN_EMAIL = "admin-appts@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong NPI_SEQ = new AtomicLong(9_900_000_000L);
    /** Each test books in its own far-future day to avoid cross-test overlaps. */
    private static final AtomicLong DAY_SEQ = new AtomicLong(1000);

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
                "firstName", "Sched", "lastName", "Patient-" + DAY_SEQ.get(),
                "dateOfBirth", "1975-03-03", "sex", "MALE")).getBody().get("id");
        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Booked", "lastName", "Dentist",
                "npi", String.valueOf(NPI_SEQ.incrementAndGet()),
                "color", "#0ea5e9")).getBody().get("id");
        operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Test Op " + NPI_SEQ.get())).getBody().get("id");

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

    private String book(Instant start, int minutes) {
        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/appointments", frontDesk, appointmentBody(start, minutes));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void bookingReturnsEnrichedAppointment() {
        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/appointments", frontDesk, appointmentBody(slotStart, 60));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("status")).isEqualTo("SCHEDULED");
        assertThat(body.get("providerLastName")).isEqualTo("Dentist");
        assertThat(body.get("color")).isEqualTo("#0ea5e9");
        assertThat((String) body.get("operatoryName")).startsWith("Test Op");
    }

    @Test
    void providerDoubleBookingIsRejected() {
        book(slotStart, 60);

        ResponseEntity<Map<String, Object>> overlap =
                api.post("/api/v1/appointments", frontDesk, appointmentBody(
                        slotStart.plus(30, ChronoUnit.MINUTES), 60));
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) overlap.getBody().get("detail")).contains("provider");
    }

    @Test
    void concurrentDoubleBookingNeverReturnsServerError() throws Exception {
        // hammer the same slot in parallel: the pre-check race is resolved by
        // the GiST exclusion constraint, which must surface as 409, never 500
        int attempts = 6;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(attempts);
        try {
            java.util.concurrent.CyclicBarrier barrier =
                    new java.util.concurrent.CyclicBarrier(attempts);
            List<java.util.concurrent.Future<HttpStatus>> results = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    return (HttpStatus) api.post("/api/v1/appointments", frontDesk,
                            appointmentBody(slotStart, 60)).getStatusCode();
                }));
            }
            List<HttpStatus> statuses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<HttpStatus> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnly(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void backToBackAppointmentsAreAllowed() {
        book(slotStart, 60);
        ResponseEntity<Map<String, Object>> adjacent =
                api.post("/api/v1/appointments", frontDesk, appointmentBody(
                        slotStart.plus(60, ChronoUnit.MINUTES), 30));
        assertThat(adjacent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cancelledSlotCanBeRebooked() {
        String id = book(slotStart, 60);
        ResponseEntity<Map<String, Object>> cancelled = api.patch(
                "/api/v1/appointments/" + id + "/status", frontDesk,
                Map.of("status", "CANCELLED", "cancelReason", "Patient request"));
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(api.post("/api/v1/appointments", frontDesk, appointmentBody(slotStart, 60))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void statusLifecycleIsEnforced() {
        String id = book(slotStart, 60);

        for (String status : List.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            ResponseEntity<Map<String, Object>> response = api.patch(
                    "/api/v1/appointments/" + id + "/status", frontDesk,
                    Map.of("status", status));
            assertThat(response.getStatusCode()).as("transition to %s", status)
                    .isEqualTo(HttpStatus.OK);
        }

        // COMPLETED is terminal
        assertThat(api.patch("/api/v1/appointments/" + id + "/status", frontDesk,
                Map.of("status", "CANCELLED")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void illegalTransitionIsRejected() {
        String id = book(slotStart, 60);
        assertThat(api.patch("/api/v1/appointments/" + id + "/status", frontDesk,
                Map.of("status", "COMPLETED")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rescheduleMovesAppointmentAndChecksConflicts() {
        String first = book(slotStart, 60);
        String second = book(slotStart.plus(2, ChronoUnit.HOURS), 60);

        // move second onto first -> conflict
        Map<String, Object> ontoFirst = appointmentBody(slotStart, 60);
        assertThat(api.put("/api/v1/appointments/" + second, frontDesk, ontoFirst)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // move second to a free slot -> ok
        Map<String, Object> freeSlot = appointmentBody(slotStart.plus(4, ChronoUnit.HOURS), 60);
        ResponseEntity<Map<String, Object>> moved =
                api.put("/api/v1/appointments/" + second, frontDesk, freeSlot);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moved.getBody().get("startsAt"))
                .isEqualTo(slotStart.plus(4, ChronoUnit.HOURS).toString());

        // first is untouched
        assertThat(api.get("/api/v1/appointments/" + first, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelledAppointmentCanBeRescheduledAndReturnsToScheduled() {
        String id = book(slotStart, 60);
        api.patch("/api/v1/appointments/" + id + "/status", frontDesk,
                Map.of("status", "CANCELLED", "cancelReason", "Sick"));

        ResponseEntity<Map<String, Object>> rebooked = api.put(
                "/api/v1/appointments/" + id, frontDesk,
                appointmentBody(slotStart.plus(1, ChronoUnit.DAYS), 60));
        assertThat(rebooked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rebooked.getBody().get("status")).isEqualTo("SCHEDULED");
        assertThat(rebooked.getBody().get("cancelledReason")).isNull();
    }

    @Test
    void noShowAppointmentCannotBeEdited() {
        String id = book(slotStart, 60);
        api.patch("/api/v1/appointments/" + id + "/status", frontDesk,
                Map.of("status", "NO_SHOW"));

        assertThat(api.put("/api/v1/appointments/" + id, frontDesk,
                appointmentBody(slotStart.plus(1, ChronoUnit.DAYS), 60)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void completedAppointmentCannotBeEdited() {
        String id = book(slotStart, 60);
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + id + "/status", frontDesk, Map.of("status", status));
        }

        assertThat(api.put("/api/v1/appointments/" + id, frontDesk,
                appointmentBody(slotStart.plus(1, ChronoUnit.DAYS), 60)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void calendarRangeQueryFiltersByProvider() {
        book(slotStart, 60);

        String from = slotStart.minus(1, ChronoUnit.HOURS).toString();
        String to = slotStart.plus(3, ChronoUnit.HOURS).toString();
        ResponseEntity<List<Map<String, Object>>> inRange = api.getList(
                "/api/v1/appointments?from=" + from + "&to=" + to + "&providerId=" + providerId,
                frontDesk);
        assertThat(inRange.getBody()).hasSize(1);

        ResponseEntity<List<Map<String, Object>>> outOfRange = api.getList(
                "/api/v1/appointments?from=" + slotStart.plus(5, ChronoUnit.HOURS)
                        + "&to=" + slotStart.plus(8, ChronoUnit.HOURS)
                        + "&providerId=" + providerId,
                frontDesk);
        assertThat(outOfRange.getBody()).isEmpty();
    }

    @Test
    void invalidReferencesAndTimesAreRejected() {
        Map<String, Object> unknownPatient = appointmentBody(slotStart, 60);
        unknownPatient.put("patientId", "00000000-0000-0000-0000-00000000dead");
        assertThat(api.post("/api/v1/appointments", frontDesk, unknownPatient).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> endBeforeStart = appointmentBody(slotStart, 60);
        endBeforeStart.put("endsAt", slotStart.minus(30, ChronoUnit.MINUTES).toString());
        assertThat(api.post("/api/v1/appointments", frontDesk, endBeforeStart).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void proceduresCanBeAttachedToAVisit() {
        String id = book(slotStart, 60);

        ResponseEntity<Map<String, Object>> catalog =
                api.get("/api/v1/procedure-codes?q=D1110", frontDesk);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes =
                (List<Map<String, Object>>) catalog.getBody().get("content");
        String codeId = (String) codes.get(0).get("id");

        ResponseEntity<Map<String, Object>> updated = api.put(
                "/api/v1/appointments/" + id + "/procedures", frontDesk,
                Map.of("procedureCodeIds", List.of(codeId)));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) updated.getBody().get("procedures");
        assertThat(procedures).hasSize(1);
        assertThat(procedures.get(0).get("code")).isEqualTo("D1110");

        // unknown code rejected
        assertThat(api.put("/api/v1/appointments/" + id + "/procedures", frontDesk,
                Map.of("procedureCodeIds",
                        List.of("00000000-0000-0000-0000-00000000beef"))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void readOnlyCanSeeButNotBook() {
        book(slotStart, 60);
        String from = slotStart.minus(1, ChronoUnit.HOURS).toString();
        String to = slotStart.plus(2, ChronoUnit.HOURS).toString();

        assertThat(api.getList("/api/v1/appointments?from=" + from + "&to=" + to, readOnly)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/appointments", readOnly,
                appointmentBody(slotStart.plus(3, ChronoUnit.HOURS), 30)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
