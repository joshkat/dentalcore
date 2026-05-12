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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyBillingIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-family@clinic.test";
    private static final String BILLING_EMAIL = "billing-family@clinic.test";
    private static final String FRONT_EMAIL = "front-family@clinic.test";
    private static final String READONLY_EMAIL = "readonly-family@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (2_1/2_2 and 3_3–9_9 belong to other classes)
    private static final AtomicLong SEQ = new AtomicLong(2_300_000_000L);

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
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        billing = api.login(BILLING_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String newPatient(String firstName) {
        return (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", firstName, "lastName", "Family" + SEQ.incrementAndGet(),
                "dateOfBirth", "1980-01-01", "sex", "FEMALE")).getBody().get("id");
    }

    private ResponseEntity<Map<String, Object>> setGuarantor(String patientId,
                                                             String guarantorId,
                                                             HttpHeaders headers) {
        return api.put("/api/v1/patients/" + patientId + "/guarantor", headers,
                Collections.singletonMap("guarantorPatientId", guarantorId));
    }

    private void charge(String patientId, int amount) {
        assertThat(api.post("/api/v1/billing/charges", billing, Map.of(
                "patientId", patientId, "amount", amount,
                "description", "Family test charge")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private void pay(String patientId, int amount) {
        assertThat(api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", amount, "method", "CASH"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void guarantorAssignmentIsValidatedAndOneLevelOnly() {
        String parent = newPatient("Parent");
        String child = newPatient("Child");
        String grandchild = newPatient("Grandchild");

        // happy path: the response carries the guarantor's name
        ResponseEntity<Map<String, Object>> linked = setGuarantor(child, parent, billing);
        assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(linked.getBody().get("guarantorId")).isEqualTo(parent);
        assertThat(linked.getBody().get("guarantorFirstName")).isEqualTo("Parent");
        assertThat(linked.getBody().get("guarantorLastName")).isNotNull();

        // a patient cannot guarantee themself
        assertThat(setGuarantor(parent, parent, billing).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // chains are rejected: child already has a guarantor
        assertThat(setGuarantor(grandchild, child, billing).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // ... and a guarantor cannot be placed under someone else
        assertThat(setGuarantor(parent, grandchild, billing).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // unknown guarantor
        assertThat(setGuarantor(grandchild, java.util.UUID.randomUUID().toString(), billing)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // an INACTIVE patient cannot guarantee an account
        String inactive = newPatient("Inactive");
        api.patch("/api/v1/patients/" + inactive + "/status", frontDesk,
                Map.of("status", "INACTIVE"));
        assertThat(setGuarantor(grandchild, inactive, billing).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // null clears the link (self-guaranteed again)
        ResponseEntity<Map<String, Object>> cleared = setGuarantor(child, null, billing);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("guarantorId")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void familyLedgerAggregatesGuarantorAndDependents() {
        String parent = newPatient("Guarantor");
        String child = newPatient("Dependent");
        assertThat(setGuarantor(child, parent, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        charge(parent, 100);
        charge(child, 50);
        pay(child, 20);

        ResponseEntity<Map<String, Object>> response =
                api.get("/api/v1/billing/family-ledger?guarantorId=" + parent, billing);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("guarantorId")).isEqualTo(parent);
        assertThat((String) body.get("guarantorName")).contains("Guarantor");

        List<Map<String, Object>> members = (List<Map<String, Object>>) body.get("members");
        assertThat(members).hasSize(2);
        assertThat(members).anyMatch(m -> parent.equals(m.get("patientId"))
                && ((Number) m.get("balance")).doubleValue() == 100.0);
        assertThat(members).anyMatch(m -> child.equals(m.get("patientId"))
                && ((Number) m.get("balance")).doubleValue() == 30.0);
        assertThat(((Number) body.get("totalBalance")).doubleValue()).isEqualTo(130.0);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        assertThat(entries).hasSize(3);
        assertThat(entries).allMatch(e -> e.get("patientId") != null
                && e.get("patientName") != null && e.get("entryDate") != null);
        assertThat(entries).anyMatch(e -> child.equals(e.get("patientId"))
                && "PAYMENT".equals(e.get("type"))
                && ((Number) e.get("amount")).doubleValue() == -20.0);

        // looking the family up by the dependent only shows their own account
        Map<String, Object> childView = api
                .get("/api/v1/billing/family-ledger?guarantorId=" + child, billing).getBody();
        assertThat((List<?>) childView.get("members")).hasSize(1);

        // unknown guarantor is a 400
        assertThat(api.get("/api/v1/billing/family-ledger?guarantorId="
                + java.util.UUID.randomUUID(), billing).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void familyStatementRendersAPdf() {
        String parent = newPatient("Statement");
        String child = newPatient("Junior");
        setGuarantor(child, parent, billing);
        charge(parent, 75);
        charge(child, 25);

        LocalDate today = LocalDate.now();
        ResponseEntity<byte[]> pdf = rest.exchange(
                "/api/v1/billing/family-statement?guarantorId=" + parent
                        + "&from=" + today.minusDays(30) + "&to=" + today.plusDays(1),
                HttpMethod.GET, new HttpEntity<>(null, frontDesk), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void rbacIsEnforced() {
        String parent = newPatient("Rbac");
        // READ_ONLY may not see the family ledger or set guarantors
        assertThat(api.get("/api/v1/billing/family-ledger?guarantorId=" + parent, readOnly)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(setGuarantor(parent, null, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // the family statement follows the statement endpoint's roles
        LocalDate today = LocalDate.now();
        assertThat(rest.exchange(
                "/api/v1/billing/family-statement?guarantorId=" + parent
                        + "&from=" + today.minusDays(1) + "&to=" + today,
                HttpMethod.GET, new HttpEntity<>(null, readOnly), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // ADMIN can manage guarantors too
        assertThat(setGuarantor(parent, null, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
