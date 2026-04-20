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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-avail@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(4_400_000_000L);

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
    private String patientId;
    private String providerId;
    private String operatoryId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        if (userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).isEmpty()) {
            User user = new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "A", "A", null);
            user.setRoles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()));
            userRepository.save(user);
        }
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Avail", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-01-01", "sex", "MALE")).getBody().get("id");
        providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Hours", "lastName", "Prov" + SEQ.get(),
                "npi", String.valueOf(SEQ.get()))).getBody().get("id");
        operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Avail Op " + SEQ.get())).getBody().get("id");
    }

    /** A Tuesday far in the future, in the clinic timezone (America/New_York). */
    private ZonedDateTime futureTuesdayAt(int hour) {
        LocalDate date = LocalDate.now().plusDays(SEQ.get() % 300 + 3500);
        while (date.getDayOfWeek() != DayOfWeek.TUESDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(hour, 0).atZone(java.time.ZoneId.of("America/New_York"));
    }

    private ResponseEntity<Map<String, Object>> book(ZonedDateTime start, int minutes) {
        return api.post("/api/v1/appointments", admin, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.withZoneSameInstant(ZoneOffset.UTC).toInstant().toString(),
                "endsAt", start.plusMinutes(minutes)
                        .withZoneSameInstant(ZoneOffset.UTC).toInstant().toString()));
    }

    @Test
    void noTemplateMeansAlwaysBookable() {
        assertThat(book(futureTuesdayAt(6), 30).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void workingHoursAreEnforcedOnBooking() {
        // Tuesday 9-17 only
        ResponseEntity<List<Map<String, Object>>> saved = rest.exchange(
                "/api/v1/providers/" + providerId + "/hours", HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(
                        List.of(Map.of("dayOfWeek", 2, "startTime", "09:00",
                                "endTime", "17:00")), admin),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).hasSize(1);

        // in hours
        assertThat(book(futureTuesdayAt(10), 60).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // before opening
        ResponseEntity<Map<String, Object>> early = book(futureTuesdayAt(7), 60);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) early.getBody().get("detail")).contains("working hours");
        // Wednesday is not in the template
        assertThat(book(futureTuesdayAt(10).plusDays(1), 60).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void timeOffBlocksBooking() {
        ZonedDateTime vacationDay = futureTuesdayAt(0).plusDays(14);
        ResponseEntity<Map<String, Object>> timeOff = api.post(
                "/api/v1/providers/" + providerId + "/time-off", admin,
                Map.of("startsAt", vacationDay.toInstant().toString(),
                        "endsAt", vacationDay.plusDays(1).toInstant().toString(),
                        "reason", "Vacation"));
        assertThat(timeOff.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> blocked = book(vacationDay.plusHours(10), 60);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) blocked.getBody().get("detail")).contains("Vacation");

        // removing time off frees the slot
        String timeOffId = (String) timeOff.getBody().get("id");
        api.exchange("/api/v1/providers/" + providerId + "/time-off/" + timeOffId,
                HttpMethod.DELETE, admin, null);
        assertThat(book(vacationDay.plusHours(10), 60).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
}
