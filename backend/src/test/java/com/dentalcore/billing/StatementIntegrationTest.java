package com.dentalcore.billing;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StatementIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-stmt@clinic.test";
    private static final String BILLING_EMAIL = "billing-stmt@clinic.test";
    private static final String READONLY_EMAIL = "readonly-stmt@clinic.test";
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
    private HttpHeaders admin;
    private HttpHeaders billing;
    private HttpHeaders readOnly;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        billing = api.login(BILLING_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "T", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    @Test
    void statementRendersAsPdfWithLedgerActivity() {
        String patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Stmt", "lastName", "Patient" + System.nanoTime(),
                "dateOfBirth", "1980-08-08", "sex", "MALE")).getBody().get("id");
        // em-dash matches auto-charge descriptions ("D2950 — …"): must survive XHTML escaping
        api.post("/api/v1/billing/charges", billing, Map.of(
                "patientId", patientId, "amount", 250, "description", "D2950 — Crown lab fee"));
        api.post("/api/v1/billing/payments", billing, Map.of(
                "patientId", patientId, "amount", 50, "method", "CASH"));

        LocalDate today = LocalDate.now();
        ResponseEntity<byte[]> pdf = rest.exchange(
                "/api/v1/billing/statement?patientId=" + patientId
                        + "&from=" + today.minusDays(30) + "&to=" + today.plusDays(1),
                HttpMethod.GET, new HttpEntity<>(null, billing), byte[].class);

        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getBody()).isNotEmpty();
        // %PDF magic bytes
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");

        // read-only cannot generate statements
        assertThat(rest.exchange(
                "/api/v1/billing/statement?patientId=" + patientId
                        + "&from=" + today.minusDays(30) + "&to=" + today,
                HttpMethod.GET, new HttpEntity<>(null, readOnly), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
