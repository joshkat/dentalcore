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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch statement runs. The shared database accumulates accounts from every
 * test class, so each scenario isolates itself with balances in a
 * multi-million band no other test reaches and a minBalance that fences off
 * everything below its own band.
 */
class StatementRunIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-stmtrun@clinic.test";
    private static final String BILLING_EMAIL = "billing-stmtrun@clinic.test";
    private static final String FRONT_EMAIL = "front-stmtrun@clinic.test";
    private static final String READONLY_EMAIL = "readonly-stmtrun@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (other classes use 1_234_567, 2_1–2_7 and 3_3–9_9)
    private static final AtomicLong SEQ = new AtomicLong(2_800_000_000L);

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
                "firstName", firstName, "lastName", "StmtRun" + SEQ.incrementAndGet(),
                "dateOfBirth", "1980-01-01", "sex", "FEMALE")).getBody().get("id");
    }

    private void setGuarantor(String patientId, String guarantorId) {
        assertThat(api.put("/api/v1/patients/" + patientId + "/guarantor", billing,
                Collections.singletonMap("guarantorPatientId", guarantorId))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void charge(String patientId, long amount) {
        assertThat(api.post("/api/v1/billing/charges", billing, Map.of(
                "patientId", patientId, "amount", amount,
                "description", "Statement run test charge")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private void pay(String patientId, long amount) {
        assertThat(api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", amount, "method", "CASH"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map<String, Object>> postRun(HttpHeaders headers,
                                                        LocalDate from, LocalDate to,
                                                        long minBalance) {
        return api.post("/api/v1/billing/statement-runs", headers, Map.of(
                "fromDate", from.toString(), "toDate", to.toString(),
                "minBalance", minBalance));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> run) {
        return (List<Map<String, Object>>) run.get("items");
    }

    private static double num(Object value) {
        return ((Number) value).doubleValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runFilesStatementsForEveryEligibleFamilyAccount() {
        // band: 6.0M–7.0M; minBalance fences out everything below 6.0M
        String parent = newPatient("Guarantor");
        String child = newPatient("Dependent");
        setGuarantor(child, parent);
        charge(parent, 6_000_000);
        charge(child, 1_500_000);
        pay(child, 500_000); // family balance 7,000,000

        String solo = newPatient("Solo");
        charge(solo, 6_500_000); // self-guaranteed balance 6,500,000

        LocalDate today = LocalDate.now();
        ResponseEntity<Map<String, Object>> response =
                postRun(billing, today.minusDays(30), today, 6_000_000);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> run = response.getBody();
        assertThat(run.get("id")).isNotNull();
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("fromDate")).isEqualTo(today.minusDays(30).toString());
        assertThat(run.get("toDate")).isEqualTo(today.toString());
        assertThat(num(run.get("minBalance"))).isEqualTo(6_000_000.0);
        assertThat(num(run.get("totalAccounts"))).isEqualTo(2.0);
        assertThat(num(run.get("totalAmount"))).isEqualTo(13_500_000.0);
        assertThat(run.get("createdAt")).isNotNull();

        List<Map<String, Object>> items = items(run);
        assertThat(items).hasSize(2);
        Map<String, Object> familyItem = items.stream()
                .filter(i -> parent.equals(i.get("guarantorPatientId")))
                .findFirst().orElseThrow();
        assertThat(num(familyItem.get("balance"))).isEqualTo(7_000_000.0);
        assertThat((String) familyItem.get("guarantorName")).contains("StmtRun");
        assertThat(familyItem.get("documentId")).isNotNull();
        Map<String, Object> soloItem = items.stream()
                .filter(i -> solo.equals(i.get("guarantorPatientId")))
                .findFirst().orElseThrow();
        assertThat(num(soloItem.get("balance"))).isEqualTo(6_500_000.0);
        assertThat(soloItem.get("documentId")).isNotNull();

        // the dependent gets no statement of their own — only the guarantor
        assertThat(items).noneMatch(i -> child.equals(i.get("guarantorPatientId")));

        // each statement is filed in the guarantor's Documents as a STATEMENT
        for (Map.Entry<String, Object> account : Map.of(
                parent, familyItem.get("documentId"),
                solo, soloItem.get("documentId")).entrySet()) {
            Map<String, Object> page = api.get(
                    "/api/v1/documents?patientId=" + account.getKey(), billing).getBody();
            List<Map<String, Object>> documents =
                    (List<Map<String, Object>>) page.get("content");
            Map<String, Object> document = documents.stream()
                    .filter(d -> account.getValue().equals(d.get("id")))
                    .findFirst().orElseThrow();
            assertThat(document.get("category")).isEqualTo("STATEMENT");
            assertThat((String) document.get("filename"))
                    .isEqualTo("Statement " + today + ".pdf");

            ResponseEntity<byte[]> pdf = rest.exchange(
                    "/api/v1/documents/" + account.getValue() + "/download",
                    HttpMethod.GET, new HttpEntity<>(null, billing), byte[].class);
            assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");
        }
    }

    @Test
    void minBalanceFilterExcludesSmallAccounts() {
        // band: 2.0M–5.0M; the cutoff sits between the two accounts
        String big = newPatient("Big");
        charge(big, 5_000_000);
        String small = newPatient("Small");
        charge(small, 2_000_000);

        LocalDate today = LocalDate.now();
        ResponseEntity<Map<String, Object>> response =
                postRun(admin, today.minusDays(30), today, 4_000_000);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Map<String, Object>> items = items(response.getBody());
        assertThat(items).anyMatch(i -> big.equals(i.get("guarantorPatientId"))
                && num(i.get("balance")) == 5_000_000.0);
        assertThat(items).noneMatch(i -> small.equals(i.get("guarantorPatientId")));

        // the excluded account received no statement document
        Map<String, Object> page =
                api.get("/api/v1/documents?patientId=" + small, billing).getBody();
        assertThat((List<Map<String, Object>>) page.get("content"))
                .noneMatch(d -> "STATEMENT".equals(d.get("category")));
    }

    @Test
    void runWithNoEligibleAccountsCompletesEmpty() {
        LocalDate today = LocalDate.now();
        ResponseEntity<Map<String, Object>> response =
                postRun(billing, today.minusDays(30), today, 99_000_000);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> run = response.getBody();
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(num(run.get("totalAccounts"))).isEqualTo(0.0);
        assertThat(num(run.get("totalAmount"))).isEqualTo(0.0);
        assertThat(items(run)).isEmpty();
    }

    @Test
    void invalidDatesAndMinBalanceAreRejected() {
        LocalDate today = LocalDate.now();
        // fromDate after toDate
        assertThat(postRun(billing, today, today.minusDays(1), 0).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // negative minBalance
        assertThat(postRun(billing, today.minusDays(30), today, -5).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // missing dates
        assertThat(api.post("/api/v1/billing/statement-runs", billing,
                Map.of("minBalance", 0)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rbacAllowsOnlyAdminAndBilling() {
        LocalDate today = LocalDate.now();
        assertThat(postRun(readOnly, today.minusDays(30), today, 99_000_000)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postRun(frontDesk, today.minusDays(30), today, 99_000_000)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/billing/statement-runs", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/billing/statement-runs", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.getList("/api/v1/billing/statement-runs", admin).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.getList("/api/v1/billing/statement-runs", billing).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void listIsNewestFirstWithoutItemsAndDetailResolvesNames() {
        LocalDate today = LocalDate.now();
        String first = (String) postRun(admin, today.minusDays(60), today, 98_000_000)
                .getBody().get("id");
        String second = (String) postRun(admin, today.minusDays(30), today, 98_000_000)
                .getBody().get("id");

        List<Map<String, Object>> runs =
                api.getList("/api/v1/billing/statement-runs", billing).getBody();
        assertThat(runs.size()).isLessThanOrEqualTo(50);
        List<String> ids = runs.stream().map(r -> (String) r.get("id")).toList();
        assertThat(ids).contains(first, second);
        assertThat(ids.indexOf(second)).isLessThan(ids.indexOf(first));
        assertThat(runs).allMatch(r -> !r.containsKey("items")
                && r.get("status") != null && r.get("createdAt") != null
                && r.get("fromDate") != null && r.get("toDate") != null
                && r.get("minBalance") != null && r.get("totalAccounts") != null
                && r.get("totalAmount") != null);

        ResponseEntity<Map<String, Object>> detail =
                api.get("/api/v1/billing/statement-runs/" + second, billing);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("id")).isEqualTo(second);
        assertThat(detail.getBody().get("fromDate"))
                .isEqualTo(today.minusDays(30).toString());
        assertThat(items(detail.getBody())).isEmpty();

        // unknown run id
        assertThat(api.get("/api/v1/billing/statement-runs/" + UUID.randomUUID(), billing)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
