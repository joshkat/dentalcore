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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EstimateIntegrationTest extends IntegrationTest {

    private static final String BILLING_EMAIL = "billing-est@clinic.test";
    private static final String ADMIN_EMAIL = "admin-est@clinic.test";
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
    @Autowired
    private com.dentalcore.insurance.internal.repository.ClaimRepository claimRepository;

    private ApiTestClient api;
    private HttpHeaders billing;
    private HttpHeaders admin;
    private String patientId;
    private String planId;
    private String prophyId; // D1110, standard 120
    private String crownId;  // D2740, standard 1250

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(ADMIN_EMAIL, "ADMIN");
        billing = api.login(BILLING_EMAIL, PASSWORD);
        admin = api.login(ADMIN_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Est", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1987-07-07", "sex", "FEMALE")).getBody().get("id");

        String carrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", "Estimate Carrier " + SEQ.get())).getBody().get("id");
        planId = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", carrierId, "planName", "Est PPO", "planType", "PPO",
                "annualMax", 1000, "deductible", 50)).getBody().get("id");
        api.post("/api/v1/patient-insurance", billing, Map.of(
                "patientId", patientId, "planId", planId,
                "subscriberPatientId", patientId, "relationshipToSubscriber", "SELF",
                "memberId", "EST-" + SEQ.get(), "priority", "PRIMARY"));

        prophyId = findCodeId("D1110");
        crownId = findCodeId("D2740");
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

    private void configurePlan() {
        // fee schedule: D1110 allowed 100 (gross 120), D2740 allowed 800 (gross 1250)
        String scheduleId = (String) api.post("/api/v1/insurance/fee-schedules", billing,
                Map.of("name", "PPO Schedule " + SEQ.get() + "-" + System.nanoTime()))
                .getBody().get("id");
        rest.exchange("/api/v1/insurance/fee-schedules/" + scheduleId + "/fees",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of(
                        Map.of("procedureCodeId", prophyId, "fee", 100),
                        Map.of("procedureCodeId", crownId, "fee", 800)), billing),
                String.class);
        rest.exchange("/api/v1/insurance/plans/" + planId + "/fee-schedule",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(
                        Map.of("feeScheduleId", scheduleId), billing), Void.class);
        rest.exchange("/api/v1/insurance/plans/" + planId + "/coverage-rules",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of(
                        Map.of("category", "PREVENTIVE", "coveragePercent", 100),
                        Map.of("category", "RESTORATIVE", "coveragePercent", 50)), billing),
                String.class);
    }

    @Test
    void estimateAppliesScheduleCoverageAndDeductible() {
        configurePlan();

        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/insurance/estimate",
                billing, Map.of("patientId", patientId, "items", List.of(
                        Map.of("procedureCodeId", prophyId),
                        Map.of("procedureCodeId", crownId))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = response.getBody();
        assertThat(result.get("hasCoverage")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) result.get("lines");
        // D1110: allowed 100, deductible 50 applied, insurance = (100-50)*100% = 50
        Map<String, Object> prophy = lines.get(0);
        assertThat(num(prophy.get("allowedFee"))).isEqualTo(100.0);
        assertThat(num(prophy.get("deductibleApplied"))).isEqualTo(50.0);
        assertThat(num(prophy.get("insuranceEstimate"))).isEqualTo(50.0);
        assertThat(num(prophy.get("patientPortion"))).isEqualTo(50.0);
        assertThat(num(prophy.get("writeOff"))).isEqualTo(20.0); // 120 gross - 100 allowed

        // D2740: allowed 800, deductible exhausted, insurance = 800*50% = 400
        Map<String, Object> crown = lines.get(1);
        assertThat(num(crown.get("insuranceEstimate"))).isEqualTo(400.0);
        assertThat(num(crown.get("patientPortion"))).isEqualTo(400.0);
        assertThat(num(crown.get("writeOff"))).isEqualTo(450.0);

        assertThat(num(result.get("totalInsurance"))).isEqualTo(450.0);
        assertThat(num(result.get("totalPatient"))).isEqualTo(450.0);
    }

    @Test
    void annualMaxCapsInsurancePortion() {
        configurePlan();
        // two crowns: ins would be 400 + 400 but the second is capped by remaining max
        // after deductible math: line1 = (800-50)*0.5 = 375; line2 = 400; total 775 < 1000 — fine.
        // Use four crowns to exceed the 1000 max: 375 + 400 + 225(capped) + 0
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/insurance/estimate",
                billing, Map.of("patientId", patientId, "items", List.of(
                        Map.of("procedureCodeId", crownId),
                        Map.of("procedureCodeId", crownId),
                        Map.of("procedureCodeId", crownId),
                        Map.of("procedureCodeId", crownId))));
        Map<String, Object> result = response.getBody();
        assertThat(num(result.get("totalInsurance"))).isEqualTo(1000.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) result.get("lines");
        assertThat(num(lines.get(3).get("insuranceEstimate"))).isEqualTo(0.0);
    }

    @Test
    void noCoverageMeansPatientPaysGross() {
        String uninsured = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "NoIns", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1999-09-09", "sex", "MALE")).getBody().get("id");
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/insurance/estimate",
                billing, Map.of("patientId", uninsured, "items", List.of(
                        Map.of("procedureCodeId", prophyId))));
        Map<String, Object> result = response.getBody();
        assertThat(result.get("hasCoverage")).isEqualTo(false);
        assertThat(num(result.get("totalPatient"))).isEqualTo(120.0);
        assertThat(num(result.get("totalInsurance"))).isEqualTo(0.0);
    }

    @Test
    void treatmentPlanEstimateAndScheduleBilledClaimsWork() {
        configurePlan();
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Est", "lastName", "Prov" + SEQ.get(),
                "npi", String.valueOf(1_100_000_000L + SEQ.incrementAndGet())))
                .getBody().get("id");
        String tpId = (String) api.post("/api/v1/treatment-plans", admin, Map.of(
                "patientId", patientId, "providerId", providerId, "title", "Est plan"))
                .getBody().get("id");
        api.post("/api/v1/treatment-plans/" + tpId + "/procedures", admin,
                Map.of("procedureCodeId", crownId, "tooth", "14"));

        ResponseEntity<Map<String, Object>> estimate =
                api.get("/api/v1/treatment-plans/" + tpId + "/estimate", admin);
        assertThat(estimate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(num(estimate.getBody().get("totalInsurance"))).isEqualTo(375.0);

        // claim line billed defaults to the schedule fee (800), not the standard fee (1250)
        String coverageId = (String) ((List<Map<String, Object>>) (Object) api.getList(
                "/api/v1/patient-insurance?patientId=" + patientId, billing).getBody())
                .get(0).get("id");
        String claimId = (String) api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId)).getBody().get("id");
        ResponseEntity<Map<String, Object>> withLine = api.post(
                "/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", crownId));
        assertThat(num(withLine.getBody().get("totalBilled"))).isEqualTo(800.0);
    }

    @Test
    void deductibleAccumulatesAcrossPaidClaims() {
        // dedicated patient + plan: deductible 100, D1110 allowed 60, PREVENTIVE 100%
        String patient = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Deduct", "lastName", "Patient" + SEQ.incrementAndGet(),
                "dateOfBirth", "1985-05-05", "sex", "MALE")).getBody().get("id");
        String carrierId = (String) api.post("/api/v1/insurance/carriers", billing,
                Map.of("name", "Deduct Carrier " + SEQ.get())).getBody().get("id");
        String plan = (String) api.post("/api/v1/insurance/plans", billing, Map.of(
                "carrierId", carrierId, "planName", "Deduct PPO", "planType", "PPO",
                "annualMax", 1000, "deductible", 100)).getBody().get("id");
        String coverageId = (String) api.post("/api/v1/patient-insurance", billing, Map.of(
                "patientId", patient, "planId", plan,
                "subscriberPatientId", patient, "relationshipToSubscriber", "SELF",
                "memberId", "DED-" + SEQ.get(), "priority", "PRIMARY")).getBody().get("id");
        String scheduleId = (String) api.post("/api/v1/insurance/fee-schedules", billing,
                Map.of("name", "Deduct Schedule " + SEQ.get() + "-" + System.nanoTime()))
                .getBody().get("id");
        rest.exchange("/api/v1/insurance/fee-schedules/" + scheduleId + "/fees",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of(
                        Map.of("procedureCodeId", prophyId, "fee", 60)), billing),
                String.class);
        rest.exchange("/api/v1/insurance/plans/" + plan + "/fee-schedule",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(
                        Map.of("feeScheduleId", scheduleId), billing), Void.class);
        rest.exchange("/api/v1/insurance/plans/" + plan + "/coverage-rules",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of(
                        Map.of("category", "PREVENTIVE", "coveragePercent", 100)), billing),
                String.class);

        // first claim: one D1110, allowed 60, paid → consumes 60 of the deductible
        String claimId = (String) api.post("/api/v1/claims", billing,
                Map.of("patientInsuranceId", coverageId)).getBody().get("id");
        ResponseEntity<Map<String, Object>> withLine = api.post(
                "/api/v1/claims/" + claimId + "/procedures", billing,
                Map.of("procedureCodeId", prophyId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> claimLines =
                (List<Map<String, Object>>) withLine.getBody().get("procedures");
        String lineId = (String) claimLines.get(0).get("id");
        api.patch("/api/v1/claims/" + claimId + "/status", billing,
                Map.of("status", "SUBMITTED"));
        api.patch("/api/v1/claims/" + claimId + "/status", billing,
                Map.of("status", "ACCEPTED"));
        api.post("/api/v1/claims/" + claimId + "/procedures/" + lineId + "/payment", billing,
                Map.of("paidAmount", 20));
        api.patch("/api/v1/claims/" + claimId + "/status", billing, Map.of("status", "PAID"));

        assertThat(claimRepository.findById(UUID.fromString(claimId)).orElseThrow()
                .getDeductibleApplied()).isEqualByComparingTo("60");

        // next estimate: only the remaining 40 of the deductible applies
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/insurance/estimate",
                billing, Map.of("patientId", patient, "items", List.of(
                        Map.of("procedureCodeId", prophyId))));
        Map<String, Object> result = response.getBody();
        assertThat(num(result.get("deductibleRemaining"))).isEqualTo(40.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) result.get("lines");
        assertThat(num(lines.get(0).get("deductibleApplied"))).isEqualTo(40.0);
        assertThat(num(lines.get(0).get("insuranceEstimate"))).isEqualTo(20.0);
        assertThat(num(lines.get(0).get("patientPortion"))).isEqualTo(40.0);
    }

    @Test
    void coverageRuleAdminIsProtected() {
        HttpHeaders frontDesk;
        seedUser("front-est@clinic.test", "FRONT_DESK");
        frontDesk = api.login("front-est@clinic.test", PASSWORD);
        assertThat(rest.exchange("/api/v1/insurance/plans/" + planId + "/coverage-rules",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of(
                        Map.of("category", "PREVENTIVE", "coveragePercent", 100)), frontDesk),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private double num(Object value) {
        return ((Number) value).doubleValue();
    }
}
