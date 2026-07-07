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

import static org.assertj.core.api.Assertions.assertThat;

class PerioIntegrationTest extends IntegrationTest {

    private static final String HYGIENIST_EMAIL = "hygienist-perio@clinic.test";
    private static final String FRONT_EMAIL = "front-perio@clinic.test";
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
    private HttpHeaders hygienist;
    private HttpHeaders frontDesk;
    private String patientId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(HYGIENIST_EMAIL, "HYGIENIST");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        hygienist = api.login(HYGIENIST_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        patientId = (String) api.post("/api/v1/patients", hygienist, Map.of(
                "firstName", "Perio", "lastName", "Patient" + System.nanoTime(),
                "dateOfBirth", "1975-05-05", "sex", "FEMALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "T", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String createExam() {
        ResponseEntity<Map<String, Object>> exam = api.post(
                "/api/v1/patients/" + patientId + "/perio-exams", hygienist, Map.of());
        assertThat(exam.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) exam.getBody().get("id");
    }

    @Test
    void measurementsUpsertAndSummaryStatsWork() {
        String examId = createExam();

        // initial batch: tooth 3 has a 6mm bleeding pocket, tooth 14 healthy
        ResponseEntity<Map<String, Object>> saved = api.put(
                "/api/v1/patients/" + patientId + "/perio-exams/" + examId + "/measurements",
                hygienist, Map.of(
                        "measurements", List.of(
                                Map.of("tooth", "16", "site", 1, "pocketDepth", 6, "bleeding", true),
                                Map.of("tooth", "16", "site", 2, "pocketDepth", 4),
                                Map.of("tooth", "26", "site", 1, "pocketDepth", 2)),
                        "toothFindings", List.of(
                                Map.of("tooth", "16", "mobility", 1))));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) saved.getBody().get("measurements")).hasSize(3);

        // upsert: same site overwritten, not duplicated
        api.put("/api/v1/patients/" + patientId + "/perio-exams/" + examId + "/measurements",
                hygienist, Map.of("measurements", List.of(
                        Map.of("tooth", "16", "site", 1, "pocketDepth", 5, "bleeding", false))));
        ResponseEntity<Map<String, Object>> reloaded = api.get(
                "/api/v1/patients/" + patientId + "/perio-exams/" + examId, hygienist);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sites =
                (List<Map<String, Object>>) reloaded.getBody().get("measurements");
        assertThat(sites).hasSize(3);
        Map<String, Object> site1 = sites.stream()
                .filter(s -> "16".equals(s.get("tooth")) && Integer.valueOf(1).equals(s.get("site")))
                .findFirst().orElseThrow();
        assertThat(site1.get("pocketDepth")).isEqualTo(5);
        assertThat(site1.get("bleeding")).isEqualTo(false);

        // summary
        ResponseEntity<List<Map<String, Object>>> list = api.getList(
                "/api/v1/patients/" + patientId + "/perio-exams", hygienist);
        Map<String, Object> summary = list.getBody().get(0);
        assertThat(summary.get("sitesRecorded")).isEqualTo(3);
        assertThat(summary.get("sites4mmPlus")).isEqualTo(2); // 5mm and 4mm
        assertThat(summary.get("sites6mmPlus")).isEqualTo(0); // 6mm was corrected to 5
    }

    @Test
    void validationRejectsBadSitesTeethAndDepths() {
        String examId = createExam();
        String base = "/api/v1/patients/" + patientId + "/perio-exams/" + examId + "/measurements";

        // site 7
        assertThat(api.put(base, hygienist, Map.of("measurements", List.of(
                Map.of("tooth", "16", "site", 7, "pocketDepth", 3)))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // primary tooth (perio is permanent dentition only)
        assertThat(api.put(base, hygienist, Map.of("measurements", List.of(
                Map.of("tooth", "55", "site", 1, "pocketDepth", 3)))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // depth 25
        assertThat(api.put(base, hygienist, Map.of("measurements", List.of(
                Map.of("tooth", "16", "site", 1, "pocketDepth", 25)))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void frontDeskCanViewButNotChartAndDeleteWorks() {
        String examId = createExam();

        assertThat(api.getList("/api/v1/patients/" + patientId + "/perio-exams", frontDesk)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/patients/" + patientId + "/perio-exams", frontDesk,
                Map.of()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(api.delete("/api/v1/patients/" + patientId + "/perio-exams/" + examId,
                hygienist).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.get("/api/v1/patients/" + patientId + "/perio-exams/" + examId,
                hygienist).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
