package com.dentalcore.insurance;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InsuranceIntegrationTest extends IntegrationTest {

    private static final String BILLING_EMAIL = "billing-ins@clinic.test";
    private static final String FRONT_EMAIL = "front-ins@clinic.test";
    private static final String READONLY_EMAIL = "readonly-ins@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicInteger SEQ = new AtomicInteger(0);

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
    private String patientId;
    private String planId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        billing = api.login(BILLING_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Insured", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-09-09", "sex", "FEMALE")).getBody().get("id");

        String carrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", "Delta Dental Test " + SEQ.get(), "payerId", "12345"))
                .getBody().get("id");
        planId = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", carrierId, "planName", "PPO Gold", "planType", "PPO",
                "annualMax", 1500, "deductible", 50)).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private Map<String, Object> selfCoverage() {
        return Map.of(
                "patientId", patientId,
                "planId", planId,
                "subscriberPatientId", patientId,
                "relationshipToSubscriber", "SELF",
                "memberId", "MBR-" + SEQ.get(),
                "priority", "PRIMARY");
    }

    private String addCoverage() {
        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/patient-insurance", frontDesk, selfCoverage());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void coverageEnrichesPlanCarrierAndSubscriber() {
        addCoverage();

        ResponseEntity<List<Map<String, Object>>> list =
                api.getList("/api/v1/patient-insurance?patientId=" + patientId, readOnly);
        assertThat(list.getBody()).hasSize(1);
        Map<String, Object> coverage = list.getBody().get(0);
        assertThat(coverage.get("planName")).isEqualTo("PPO Gold");
        assertThat((String) coverage.get("carrierName")).startsWith("Delta Dental Test");
        assertThat(coverage.get("relationshipToSubscriber")).isEqualTo("SELF");
    }

    @Test
    void selfRelationshipRequiresMatchingSubscriber() {
        String otherPatient = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Sub", "lastName", "Scriber" + SEQ.incrementAndGet(),
                "dateOfBirth", "1960-01-01", "sex", "MALE")).getBody().get("id");

        Map<String, Object> mismatch = new java.util.HashMap<>(selfCoverage());
        mismatch.put("subscriberPatientId", otherPatient);
        assertThat(api.post("/api/v1/patient-insurance", frontDesk, mismatch).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // CHILD with a distinct subscriber is fine
        mismatch.put("relationshipToSubscriber", "CHILD");
        assertThat(api.post("/api/v1/patient-insurance", frontDesk, mismatch).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void claimLifecycleWithPaymentsWorks() {
        String coverageId = addCoverage();

        // create claim
        ResponseEntity<Map<String, Object>> created = api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String claimId = (String) created.getBody().get("id");

        // submitting an empty claim is rejected
        assertThat(api.patch("/api/v1/claims/" + claimId + "/status", billing,
                Map.of("status", "SUBMITTED")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // add a line (billed defaults from catalog: D1110 = 120.00)
        String codeId = findCodeId("D1110");
        ResponseEntity<Map<String, Object>> withLine = api.post(
                "/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", codeId));
        assertThat(withLine.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(withLine.getBody().get("totalBilled")).isEqualTo(120.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines =
                (List<Map<String, Object>>) withLine.getBody().get("procedures");
        String lineId = (String) lines.get(0).get("id");

        // submit locks line items
        ResponseEntity<Map<String, Object>> submitted = api.patch(
                "/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "SUBMITTED"));
        assertThat(submitted.getBody().get("submittedAt")).isNotNull();
        assertThat(api.post("/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", codeId)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // payments only after acceptance
        assertThat(api.post("/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment",
                billing, Map.of("paidAmount", 96)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "ACCEPTED"));

        // paying more than billed is rejected
        assertThat(api.post("/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment",
                billing, Map.of("paidAmount", 500)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> paid = api.post(
                "/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment",
                billing, Map.of("paidAmount", 96));
        assertThat(((Number) paid.getBody().get("totalPaid")).doubleValue()).isEqualTo(96.0);

        // finish lifecycle
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "PAID"));
        ResponseEntity<Map<String, Object>> closed = api.patch(
                "/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "CLOSED"));
        assertThat(closed.getBody().get("status")).isEqualTo("CLOSED");
    }

    @Test
    void deniedClaimCanBeResubmitted() {
        String coverageId = addCoverage();
        String claimId = (String) api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId)).getBody().get("id");
        api.post("/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", findCodeId("D0120")));

        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "SUBMITTED"));
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "DENIED"));
        ResponseEntity<Map<String, Object>> resubmitted = api.patch(
                "/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "SUBMITTED"));
        assertThat(resubmitted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void worklistFiltersByStatus() {
        String coverageId = addCoverage();
        api.post("/api/v1/claims", billing, Map.of("patientInsuranceId", coverageId));

        ResponseEntity<Map<String, Object>> drafts =
                api.get("/api/v1/claims?status=DRAFT", billing);
        assertThat(drafts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) drafts.getBody().get("content")).isNotEmpty();
    }

    @Test
    void rbacIsEnforced() {
        // front desk manages coverage but not carriers or claims
        assertThat(api.post("/api/v1/insurance/carriers", frontDesk,
                Map.of("name", "Nope Carrier")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String coverageId = addCoverage();
        assertThat(api.post("/api/v1/claims", frontDesk,
                Map.of("patientInsuranceId", coverageId)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // read-only can see everything but write nothing
        assertThat(api.get("/api/v1/claims", readOnly).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/v1/patient-insurance", readOnly, selfCoverage())
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String findCodeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, billing);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }
}
