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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coordination of benefits: dual-coverage estimates, secondary claim drafting
 * and the patient-credit path when a secondary claim is paid.
 */
class SecondaryInsuranceIntegrationTest extends IntegrationTest {

    private static final String BILLING_EMAIL = "billing-cob@clinic.test";
    private static final String ADMIN_EMAIL = "admin-cob@clinic.test";
    private static final String FRONT_DESK_EMAIL = "front-cob@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    /** Reserved NPI base for this class; also used for unique names/member ids. */
    private static final AtomicLong NPI_SEQ = new AtomicLong(2_400_000_000L);

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
    private HttpHeaders admin;
    private String crownId;  // D2740, standard 1250, RESTORATIVE
    private String prophyId; // D1110, standard 120, PREVENTIVE

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(ADMIN_EMAIL, "ADMIN");
        billing = api.login(BILLING_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        crownId = findCodeId("D2740");
        prophyId = findCodeId("D1110");
    }

    // ---- (a) dual-coverage estimate pins exact COB math ----

    @Test
    void dualCoverageEstimateCascadesTraditionalCob() {
        Coverages c = dualCoverage(1000);

        Map<String, Object> result = estimate(c.patientId(), List.of(crownId));
        assertThat(result.get("hasCoverage")).isEqualTo(true);
        assertThat(result.get("hasSecondary")).isEqualTo(true);
        assertThat(result.get("secondaryCarrierName")).isEqualTo(c.secondaryCarrierName());
        assertThat(result.get("secondaryPlanName")).isEqualTo("COB Secondary");

        // crown allowed 800; primary: deductible 50, (800-50)*0.8 = 600
        // secondary base = 800-600 = 200; secondary = min(800*0.5, 200) = 200
        Map<String, Object> line = lines(result).get(0);
        assertThat(num(line.get("allowedFee"))).isEqualTo(800.0);
        assertThat(num(line.get("deductibleApplied"))).isEqualTo(50.0);
        assertThat(num(line.get("insuranceEstimate"))).isEqualTo(600.0);
        assertThat(num(line.get("secondaryEstimate"))).isEqualTo(200.0);
        assertThat(num(line.get("patientPortion"))).isEqualTo(0.0);

        assertThat(num(result.get("totalInsurance"))).isEqualTo(600.0);
        assertThat(num(result.get("totalSecondary"))).isEqualTo(200.0);
        assertThat(num(result.get("totalPatient"))).isEqualTo(0.0);
        assertThat(num(result.get("secondaryDeductibleRemaining"))).isEqualTo(0.0);
        assertThat(num(result.get("secondaryBenefitsRemaining"))).isEqualTo(1000.0);

        // benefits endpoint exposes the nested secondary block
        Map<String, Object> benefits = api.get(
                "/api/v1/insurance/benefits?patientId=" + c.patientId(), billing).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> secondary = (Map<String, Object>) benefits.get("secondary");
        assertThat(secondary).isNotNull();
        assertThat(secondary.get("carrierName")).isEqualTo(c.secondaryCarrierName());
        assertThat(secondary.get("planName")).isEqualTo("COB Secondary");
        assertThat(num(secondary.get("deductible"))).isEqualTo(0.0);
        assertThat(num(secondary.get("deductibleRemaining"))).isEqualTo(0.0);
        assertThat(num(secondary.get("annualMax"))).isEqualTo(1000.0);
        assertThat(num(secondary.get("benefitsRemaining"))).isEqualTo(1000.0);
    }

    // ---- (b) secondary respects its own annual max ----

    @Test
    void secondaryEstimateIsCappedByItsOwnAnnualMax() {
        Coverages c = dualCoverage(150);

        Map<String, Object> result = estimate(c.patientId(), List.of(crownId));
        Map<String, Object> line = lines(result).get(0);
        // uncapped secondary would be 200, but only 150 of its annual max remains
        assertThat(num(line.get("insuranceEstimate"))).isEqualTo(600.0);
        assertThat(num(line.get("secondaryEstimate"))).isEqualTo(150.0);
        assertThat(num(line.get("patientPortion"))).isEqualTo(50.0);
        assertThat(num(result.get("totalSecondary"))).isEqualTo(150.0);
        assertThat(num(result.get("secondaryBenefitsRemaining"))).isEqualTo(150.0);
    }

    // ---- (c) no secondary coverage: response identical to primary-only today ----

    @Test
    void primaryOnlyEstimateIsUnchangedWithCobFieldsDefaulted() {
        String patientId = newPatient();
        primaryPlanWithCoverage(patientId);

        Map<String, Object> result = estimate(patientId, List.of(crownId));
        // old fields keep the exact primary-only values
        Map<String, Object> line = lines(result).get(0);
        assertThat(num(line.get("allowedFee"))).isEqualTo(800.0);
        assertThat(num(line.get("deductibleApplied"))).isEqualTo(50.0);
        assertThat(num(line.get("insuranceEstimate"))).isEqualTo(600.0);
        assertThat(num(line.get("patientPortion"))).isEqualTo(200.0);
        assertThat(num(line.get("writeOff"))).isEqualTo(450.0);
        assertThat(num(result.get("totalInsurance"))).isEqualTo(600.0);
        assertThat(num(result.get("totalPatient"))).isEqualTo(200.0);
        // new fields are inert
        assertThat(result.get("hasSecondary")).isEqualTo(false);
        assertThat(result.get("secondaryCarrierName")).isNull();
        assertThat(result.get("secondaryPlanName")).isNull();
        assertThat(result.get("secondaryDeductibleRemaining")).isNull();
        assertThat(result.get("secondaryBenefitsRemaining")).isNull();
        assertThat(num(result.get("totalSecondary"))).isEqualTo(0.0);
        assertThat(num(line.get("secondaryEstimate"))).isEqualTo(0.0);

        Map<String, Object> benefits = api.get(
                "/api/v1/insurance/benefits?patientId=" + patientId, billing).getBody();
        assertThat(benefits.get("secondary")).isNull();
        assertThat(benefits.get("hasSecondary")).isEqualTo(false);
    }

    // ---- (d) secondary claim drafting ----

    @Test
    void secondaryClaimBillsAllowedMinusPrimaryPaidAndDropsSettledLines() {
        Coverages c = dualCoverage(1000);

        // primary claim: crown (allowed 800) paid 600, prophy (allowed 100) paid in full
        String claimId = newClaim(c.primaryCoverageId());
        String crownLine = addLine(claimId, crownId);
        String prophyLine = addLine(claimId, prophyId);
        moveStatus(claimId, "SUBMITTED");
        moveStatus(claimId, "ACCEPTED");
        pay(claimId, crownLine, 600);
        pay(claimId, prophyLine, 100);
        moveStatus(claimId, "PAID");

        ResponseEntity<Map<String, Object>> created = api.post(
                "/api/v1/insurance/claims/" + claimId + "/secondary", billing, null);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> secondaryClaim = created.getBody();
        assertThat(secondaryClaim.get("patientInsuranceId")).isEqualTo(c.secondaryCoverageId());
        assertThat(secondaryClaim.get("parentClaimId")).isEqualTo(claimId);
        assertThat(secondaryClaim.get("status")).isEqualTo("DRAFT");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) secondaryClaim.get("procedures");
        assertThat(procedures).hasSize(1); // prophy line settled in full -> dropped
        assertThat(procedures.get(0).get("procedureCodeId")).isEqualTo(crownId);
        assertThat(num(procedures.get(0).get("billedAmount"))).isEqualTo(200.0);
        assertThat(num(secondaryClaim.get("totalBilled"))).isEqualTo(200.0);

        // reverse lookup on the primary claim
        Map<String, Object> primary = api.get("/api/v1/claims/" + claimId, billing).getBody();
        assertThat(primary.get("secondaryClaimId")).isEqualTo(secondaryClaim.get("id"));
        assertThat(primary.get("parentClaimId")).isNull();

        // duplicate secondary -> 409
        assertThat(api.post("/api/v1/insurance/claims/" + claimId + "/secondary",
                billing, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // non-PAID source -> 400
        String draftClaim = newClaim(c.primaryCoverageId());
        assertThat(api.post("/api/v1/insurance/claims/" + draftClaim + "/secondary",
                billing, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void secondaryClaimWithNothingLeftToBillIsRejected() {
        Coverages c = dualCoverage(1000);

        String claimId = newClaim(c.primaryCoverageId());
        String crownLine = addLine(claimId, crownId);
        moveStatus(claimId, "SUBMITTED");
        moveStatus(claimId, "ACCEPTED");
        pay(claimId, crownLine, 800); // primary pays the full allowed fee
        moveStatus(claimId, "PAID");

        ResponseEntity<Map<String, Object>> response = api.post(
                "/api/v1/insurance/claims/" + claimId + "/secondary", billing, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- (e) paying the secondary claim credits the patient ledger ----

    @Test
    void payingSecondaryClaimPostsPatientCreditThroughExistingListener() {
        Coverages c = dualCoverage(1000);

        String claimId = newClaim(c.primaryCoverageId());
        String crownLine = addLine(claimId, crownId);
        moveStatus(claimId, "SUBMITTED");
        moveStatus(claimId, "ACCEPTED");
        pay(claimId, crownLine, 600);
        moveStatus(claimId, "PAID");
        double afterPrimary = balance(c.patientId());

        String secondaryId = (String) api.post(
                "/api/v1/insurance/claims/" + claimId + "/secondary", billing, null)
                .getBody().get("id");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondaryLines = (List<Map<String, Object>>)
                api.get("/api/v1/claims/" + secondaryId, billing).getBody().get("procedures");
        String secondaryLine = (String) secondaryLines.get(0).get("id");
        moveStatus(secondaryId, "SUBMITTED");
        moveStatus(secondaryId, "ACCEPTED");
        pay(secondaryId, secondaryLine, 200);
        moveStatus(secondaryId, "PAID");

        assertThat(balance(c.patientId())).isEqualTo(afterPrimary - 200.0);
    }

    // ---- (f) RBAC ----

    @Test
    void frontDeskCannotDraftSecondaryClaims() {
        seedUser(FRONT_DESK_EMAIL, "FRONT_DESK");
        HttpHeaders frontDesk = api.login(FRONT_DESK_EMAIL, PASSWORD);
        assertThat(api.post("/api/v1/insurance/claims/" + UUID.randomUUID() + "/secondary",
                frontDesk, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- fixtures ----

    private record Coverages(String patientId, String primaryCoverageId,
                             String secondaryCoverageId, String secondaryCarrierName) {
    }

    /**
     * Patient with PRIMARY (80% RESTORATIVE / 100% PREVENTIVE, $50 deductible,
     * crown allowed 800, prophy allowed 100) and SECONDARY (50% RESTORATIVE /
     * 100% PREVENTIVE, no deductible, given annual max, no fee schedule).
     */
    private Coverages dualCoverage(int secondaryAnnualMax) {
        String patientId = newPatient();
        String primaryCoverageId = primaryPlanWithCoverage(patientId);

        long seq = NPI_SEQ.incrementAndGet();
        String secondaryCarrierName = "COB Secondary Carrier " + seq;
        String secondaryCarrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", secondaryCarrierName)).getBody().get("id");
        String secondaryPlanId = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", secondaryCarrierId, "planName", "COB Secondary",
                "planType", "PPO", "annualMax", secondaryAnnualMax, "deductible", 0))
                .getBody().get("id");
        putRules(secondaryPlanId, 50);
        String secondaryCoverageId = (String) api.post("/api/v1/patient-insurance", billing,
                Map.of("patientId", patientId, "planId", secondaryPlanId,
                        "subscriberPatientId", patientId, "relationshipToSubscriber", "SELF",
                        "memberId", "COB2-" + seq, "priority", "SECONDARY"))
                .getBody().get("id");
        return new Coverages(patientId, primaryCoverageId, secondaryCoverageId,
                secondaryCarrierName);
    }

    private String primaryPlanWithCoverage(String patientId) {
        long seq = NPI_SEQ.incrementAndGet();
        String carrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", "COB Primary Carrier " + seq)).getBody().get("id");
        String planId = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", carrierId, "planName", "COB Primary", "planType", "PPO",
                "annualMax", 5000, "deductible", 50)).getBody().get("id");
        String scheduleId = (String) api.post("/api/v1/insurance/fee-schedules", billing,
                Map.of("name", "COB Schedule " + seq + "-" + System.nanoTime()))
                .getBody().get("id");
        rest.exchange("/api/v1/insurance/fee-schedules/" + scheduleId + "/fees",
                HttpMethod.PUT, new HttpEntity<>(List.of(
                        Map.of("procedureCodeId", crownId, "fee", 800),
                        Map.of("procedureCodeId", prophyId, "fee", 100)), billing),
                String.class);
        rest.exchange("/api/v1/insurance/plans/" + planId + "/fee-schedule",
                HttpMethod.PUT, new HttpEntity<>(Map.of("feeScheduleId", scheduleId), billing),
                Void.class);
        putRules(planId, 80);
        return (String) api.post("/api/v1/patient-insurance", billing, Map.of(
                "patientId", patientId, "planId", planId,
                "subscriberPatientId", patientId, "relationshipToSubscriber", "SELF",
                "memberId", "COB1-" + seq, "priority", "PRIMARY")).getBody().get("id");
    }

    private void putRules(String planId, int restorativePercent) {
        rest.exchange("/api/v1/insurance/plans/" + planId + "/coverage-rules",
                HttpMethod.PUT, new HttpEntity<>(List.of(
                        Map.of("category", "RESTORATIVE", "coveragePercent", restorativePercent),
                        Map.of("category", "PREVENTIVE", "coveragePercent", 100)), billing),
                String.class);
    }

    private String newPatient() {
        return (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Cob", "lastName", "Patient" + NPI_SEQ.incrementAndGet(),
                "dateOfBirth", "1980-01-01", "sex", "FEMALE")).getBody().get("id");
    }

    private String newClaim(String coverageId) {
        return (String) api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId)).getBody().get("id");
    }

    private String addLine(String claimId, String procedureCodeId) {
        Map<String, Object> claim = api.post("/api/v1/claims/" + claimId + "/procedures",
                billing, Map.of("procedureCodeId", procedureCodeId)).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> procedures =
                (List<Map<String, Object>>) claim.get("procedures");
        return (String) procedures.stream()
                .filter(p -> procedureCodeId.equals(p.get("procedureCodeId")))
                .findFirst().orElseThrow().get("id");
    }

    private void moveStatus(String claimId, String status) {
        ResponseEntity<Map<String, Object>> response = api.patch(
                "/api/v1/claims/" + claimId + "/status", billing, Map.of("status", status));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void pay(String claimId, String lineId, int amount) {
        ResponseEntity<Map<String, Object>> response = api.post(
                "/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment",
                billing, Map.of("paidAmount", amount));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> estimate(String patientId, List<String> codeIds) {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/insurance/estimate",
                billing, Map.of("patientId", patientId, "items",
                        codeIds.stream().map(id -> Map.of("procedureCodeId", id)).toList()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private double balance(String patientId) {
        return ((Number) api.get("/api/v1/billing/balance?patientId=" + patientId, billing)
                .getBody().get("balance")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lines(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("lines");
    }

    private double num(Object value) {
        return ((Number) value).doubleValue();
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "T", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
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
