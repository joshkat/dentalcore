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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPlanIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-payplan@clinic.test";
    private static final String BILLING_EMAIL = "billing-payplan@clinic.test";
    private static final String FRONT_EMAIL = "front-payplan@clinic.test";
    private static final String READONLY_EMAIL = "readonly-payplan@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (2_3 belongs to FamilyBillingIntegrationTest)
    private static final AtomicLong SEQ = new AtomicLong(2_310_000_000L);

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
    private String patientId;

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

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Plan", "lastName", "Payer" + SEQ.incrementAndGet(),
                "dateOfBirth", "1975-03-03", "sex", "MALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private ResponseEntity<Map<String, Object>> createPlan(Map<String, Object> body,
                                                           HttpHeaders headers) {
        return api.post("/api/v1/billing/payment-plans", headers, body);
    }

    private Map<String, Object> basePlan(int total, int down, int installment,
                                         String frequency, LocalDate firstDueDate) {
        return Map.of(
                "patientId", patientId,
                "totalAmount", total,
                "downPayment", down,
                "installmentAmount", installment,
                "frequency", frequency,
                "firstDueDate", firstDueDate.toString(),
                "notes", "Integration plan");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduleMathExpectedAndReceived() {
        // 1000 total, 100 down, 150/month -> 900/150 = exactly 6 installments
        // first due 2 days ago so exactly one installment is already expected
        LocalDate first = LocalDate.now().minusDays(2);
        ResponseEntity<Map<String, Object>> created =
                createPlan(basePlan(1000, 100, 150, "MONTHLY", first), billing);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> plan = created.getBody();
        assertThat(plan.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) plan.get("totalAmount")).doubleValue()).isEqualTo(1000.0);
        assertThat(((Number) plan.get("downPayment")).doubleValue()).isEqualTo(100.0);

        List<Map<String, Object>> installments =
                (List<Map<String, Object>>) plan.get("installments");
        assertThat(installments).hasSize(6);
        assertThat(installments).allMatch(
                i -> ((Number) i.get("amount")).doubleValue() == 150.0);
        assertThat(installments.get(0).get("dueDate")).isEqualTo(first.toString());
        assertThat(installments.get(1).get("dueDate"))
                .isEqualTo(first.plusMonths(1).toString());
        assertThat(installments.get(5).get("dueDate"))
                .isEqualTo(first.plusMonths(5).toString());

        // expected = down payment + the one installment already due
        assertThat(((Number) plan.get("expectedToDate")).doubleValue()).isEqualTo(250.0);
        assertThat(((Number) plan.get("receivedToDate")).doubleValue()).isEqualTo(0.0);

        // payments dated on/after plan creation count toward the plan
        api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", 200, "method", "CARD"));
        List<Map<String, Object>> plans = api.getList(
                "/api/v1/billing/payment-plans?patientId=" + patientId, billing).getBody();
        Map<String, Object> reloaded = plans.stream()
                .filter(p -> plan.get("id").equals(p.get("id")))
                .findFirst().orElseThrow();
        assertThat(((Number) reloaded.get("receivedToDate")).doubleValue()).isEqualTo(200.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unevenTotalsEndWithASmallerLastInstallment() {
        // 1000 total, no down, 300 biweekly -> 300, 300, 300, then 100
        LocalDate first = LocalDate.now().plusDays(7);
        Map<String, Object> plan =
                createPlan(basePlan(1000, 0, 300, "BIWEEKLY", first), billing).getBody();
        List<Map<String, Object>> installments =
                (List<Map<String, Object>>) plan.get("installments");
        assertThat(installments).hasSize(4);
        assertThat(((Number) installments.get(0).get("amount")).doubleValue()).isEqualTo(300.0);
        assertThat(((Number) installments.get(3).get("amount")).doubleValue()).isEqualTo(100.0);
        assertThat(installments.get(1).get("dueDate"))
                .isEqualTo(first.plusWeeks(2).toString());
        // nothing due yet
        assertThat(((Number) plan.get("expectedToDate")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void statusOnlyMovesOutOfActiveOnce() {
        String planId = (String) createPlan(
                basePlan(500, 0, 100, "MONTHLY", LocalDate.now()), billing)
                .getBody().get("id");

        ResponseEntity<Map<String, Object>> completed = api.patch(
                "/api/v1/billing/payment-plans/" + planId + "/status", billing,
                Map.of("status", "COMPLETED"));
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");

        // a closed plan cannot change again
        assertThat(api.patch("/api/v1/billing/payment-plans/" + planId + "/status",
                billing, Map.of("status", "DEFAULTED")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // ACTIVE is not a valid target
        assertThat(api.patch("/api/v1/billing/payment-plans/" + planId + "/status",
                billing, Map.of("status", "ACTIVE")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requestsAreValidated() {
        // down payment must leave something to finance
        assertThat(createPlan(basePlan(500, 500, 100, "MONTHLY", LocalDate.now()), billing)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // unknown frequency
        assertThat(createPlan(basePlan(500, 0, 100, "WEEKLY", LocalDate.now()), billing)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // unknown patient
        assertThat(createPlan(Map.of(
                "patientId", java.util.UUID.randomUUID().toString(),
                "totalAmount", 500, "installmentAmount", 100,
                "frequency", "MONTHLY", "firstDueDate", LocalDate.now().toString()),
                billing).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // absurd schedules are rejected (more than 120 installments)
        assertThat(createPlan(basePlan(99999, 0, 1, "MONTHLY", LocalDate.now()), billing)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rbacIsEnforced() {
        Map<String, Object> body = basePlan(500, 0, 100, "MONTHLY", LocalDate.now());
        // write is ADMIN/BILLING only
        assertThat(createPlan(body, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(createPlan(body, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String planId = (String) createPlan(body, admin).getBody().get("id");
        assertThat(planId).isNotNull();
        // front desk may read but not change status
        assertThat(api.getList("/api/v1/billing/payment-plans?patientId=" + patientId,
                frontDesk).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/api/v1/billing/payment-plans?patientId=" + patientId,
                readOnly).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.patch("/api/v1/billing/payment-plans/" + planId + "/status",
                frontDesk, Map.of("status", "CANCELLED")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
