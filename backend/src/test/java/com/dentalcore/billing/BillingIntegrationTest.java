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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class BillingIntegrationTest extends IntegrationTest {

    private static final String BILLING_EMAIL = "billing-ledger@clinic.test";
    private static final String FRONT_EMAIL = "front-ledger@clinic.test";
    private static final String READONLY_EMAIL = "readonly-ledger@clinic.test";
    private static final String ADMIN_EMAIL = "admin-ledger@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(6_600_000_000L);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private HttpHeaders billing;
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private HttpHeaders admin;
    private String patientId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        seedUser(ADMIN_EMAIL, "ADMIN");
        billing = api.login(BILLING_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Ledger", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1992-02-02", "sex", "FEMALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private double balance() {
        return ((Number) api.get("/api/v1/billing/balance?patientId=" + patientId, billing)
                .getBody().get("balance")).doubleValue();
    }

    private String findCodeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, billing);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }

    @Test
    void chargePaymentAdjustmentRollUpToBalance() {
        // charge from procedure fee (D1110 = 120)
        ResponseEntity<Map<String, Object>> charge = api.post("/api/v1/billing/charges", billing,
                Map.of("patientId", patientId, "procedureCodeId", findCodeId("D1110")));
        assertThat(charge.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) charge.getBody().get("amount")).doubleValue()).isEqualTo(120.0);
        assertThat(balance()).isEqualTo(120.0);

        // front desk takes a card payment
        ResponseEntity<Map<String, Object>> payment = api.post("/api/v1/billing/payments",
                frontDesk, Map.of("patientId", patientId, "amount", 50, "method", "CARD"));
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) payment.getBody().get("amount")).doubleValue()).isEqualTo(-50.0);
        assertThat(balance()).isEqualTo(70.0);

        // courtesy discount
        api.post("/api/v1/billing/adjustments", billing, Map.of(
                "patientId", patientId, "amount", -20, "description", "Courtesy discount"));
        assertThat(balance()).isEqualTo(50.0);

        ResponseEntity<Map<String, Object>> ledger =
                api.get("/api/v1/billing/ledger?patientId=" + patientId, readOnly);
        assertThat(ledger.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) ledger.getBody().get("content")).hasSize(3);
        assertThat(((Number) ledger.getBody().get("balance")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void completedAppointmentAutoPostsCharges() {
        HttpHeaders clinical = admin;
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Bill", "lastName", "Auto",
                "npi", String.valueOf(SEQ.incrementAndGet()))).getBody().get("id");
        String operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "Billing Op " + SEQ.get())).getBody().get("id");

        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(SEQ.get() % 1000 + 2000, ChronoUnit.DAYS);
        String appointmentId = (String) api.post("/api/v1/appointments", clinical, Map.of(
                "patientId", patientId, "providerId", providerId, "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(1, ChronoUnit.HOURS).toString())).getBody().get("id");
        api.put("/api/v1/appointments/" + appointmentId + "/procedures", clinical,
                Map.of("procedureCodeIds", List.of(findCodeId("D1110"), findCodeId("D0120"))));

        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            assertThat(api.patch("/api/v1/appointments/" + appointmentId + "/status", clinical,
                    Map.of("status", status)).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // D1110 (120) + D0120 (65) auto-posted
        assertThat(balance()).isEqualTo(185.0);
        ResponseEntity<Map<String, Object>> ledger =
                api.get("/api/v1/billing/ledger?patientId=" + patientId, billing);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) ledger.getBody().get("content");
        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> appointmentId.equals(e.get("appointmentId")));
    }

    @Test
    void paidClaimPostsInsurancePayment() {
        String carrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", "Ledger Carrier " + SEQ.incrementAndGet())).getBody().get("id");
        String planId = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", carrierId, "planName", "Base", "planType", "PPO"))
                .getBody().get("id");
        String coverageId = (String) api.post("/api/v1/patient-insurance", billing, Map.of(
                "patientId", patientId, "planId", planId, "subscriberPatientId", patientId,
                "relationshipToSubscriber", "SELF", "memberId", "M" + SEQ.get(),
                "priority", "PRIMARY")).getBody().get("id");
        String claimId = (String) api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId)).getBody().get("id");
        ResponseEntity<Map<String, Object>> line = api.post(
                "/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", findCodeId("D1110")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines =
                (List<Map<String, Object>>) line.getBody().get("procedures");
        String lineId = (String) lines.get(0).get("id");

        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "SUBMITTED"));
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "ACCEPTED"));
        api.post("/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment", billing,
                Map.of("paidAmount", 96));
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "PAID"));

        assertThat(balance()).isEqualTo(-96.0);
        ResponseEntity<Map<String, Object>> ledger =
                api.get("/api/v1/billing/ledger?patientId=" + patientId, billing);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) ledger.getBody().get("content");
        assertThat(entries.get(0).get("type")).isEqualTo("INSURANCE_PAYMENT");
        assertThat(entries.get(0).get("claimId")).isEqualTo(claimId);
    }

    @Test
    void reversalsVoidEntriesAndCannotStack() {
        ResponseEntity<Map<String, Object>> charge = api.post("/api/v1/billing/charges", billing,
                Map.of("patientId", patientId, "amount", 200, "description", "Lab fee"));
        String entryId = (String) charge.getBody().get("id");
        assertThat(balance()).isEqualTo(200.0);

        ResponseEntity<Map<String, Object>> reversal = api.post(
                "/api/v1/billing/entries/" + entryId + "/reverse", billing,
                Map.of("reason", "posted in error"));
        assertThat(reversal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balance()).isEqualTo(0.0);

        // can't reverse twice, can't reverse a reversal
        assertThat(api.post("/api/v1/billing/entries/" + entryId + "/reverse", billing,
                Map.of("reason", "again")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String reversalId = (String) reversal.getBody().get("id");
        assertThat(api.post("/api/v1/billing/entries/" + reversalId + "/reverse", billing,
                Map.of("reason", "nope")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // original is flagged reversed in the ledger
        ResponseEntity<Map<String, Object>> ledger =
                api.get("/api/v1/billing/ledger?patientId=" + patientId, billing);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) ledger.getBody().get("content");
        assertThat(entries.stream()
                .filter(e -> entryId.equals(e.get("id")))
                .findFirst().orElseThrow().get("reversed")).isEqualTo(true);
    }

    @Test
    void rbacIsEnforced() {
        // read-only can view, not post
        assertThat(api.get("/api/v1/billing/ledger?patientId=" + patientId, readOnly)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/billing/payments", readOnly, Map.of(
                "patientId", patientId, "amount", 10, "method", "CASH")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // front desk takes payments but cannot post charges or adjustments
        assertThat(api.post("/api/v1/billing/charges", frontDesk, Map.of(
                "patientId", patientId, "amount", 10, "description", "x")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/billing/adjustments", frontDesk, Map.of(
                "patientId", patientId, "amount", -5, "description", "x")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidPostingsAreRejected() {
        // zero adjustment
        assertThat(api.post("/api/v1/billing/adjustments", billing, Map.of(
                "patientId", patientId, "amount", 0, "description", "zero")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // charge with neither amount nor procedure
        assertThat(api.post("/api/v1/billing/charges", billing, Map.of(
                "patientId", patientId, "description", "no amount")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // negative payment amount
        assertThat(api.post("/api/v1/billing/payments", billing, Map.of(
                "patientId", patientId, "amount", -10, "method", "CASH")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
