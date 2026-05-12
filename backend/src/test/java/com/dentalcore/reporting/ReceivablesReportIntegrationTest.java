package com.dentalcore.reporting;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivablesReportIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-receivables@clinic.test";
    private static final String BILLING_EMAIL = "billing-receivables@clinic.test";
    private static final String FRONT_EMAIL = "front-receivables@clinic.test";
    private static final String READONLY_EMAIL = "readonly-receivables@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    // unique base per test class — duplicate NPIs across classes fail provider
    // creation silently (2_3/2_31 belong to the family billing tests)
    private static final AtomicLong SEQ = new AtomicLong(2_320_000_000L);

    private static final UUID CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LedgerEntryRepository ledgerRepository;

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

    private String newPatient(String firstName, String phone) {
        Map<String, Object> body = phone == null
                ? Map.of("firstName", firstName,
                        "lastName", "Aging" + SEQ.incrementAndGet(),
                        "dateOfBirth", "1970-07-07", "sex", "MALE")
                : Map.of("firstName", firstName,
                        "lastName", "Aging" + SEQ.incrementAndGet(),
                        "dateOfBirth", "1970-07-07", "sex", "MALE",
                        "phones", List.of(Map.of(
                                "type", "MOBILE", "number", phone, "primary", true)));
        return (String) api.post("/api/v1/patients", frontDesk, body).getBody().get("id");
    }

    /** Backdates a charge straight through the repository to land in an aging bucket. */
    private void backdatedCharge(String patientId, int amount, int daysAgo) {
        ledgerRepository.save(LedgerEntry.charge(
                CLINIC_ID, UUID.fromString(patientId), BigDecimal.valueOf(amount),
                "Backdated charge (" + daysAgo + "d)", null, null, null)
                .at(LocalDate.now().minusDays(daysAgo)));
    }

    private void pay(String patientId, int amount) {
        assertThat(api.post("/api/v1/billing/payments", frontDesk, Map.of(
                "patientId", patientId, "amount", amount, "method", "CASH"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> agingRowFor(String guarantorId) {
        Map<String, Object> report = api.get("/api/v1/reports/ar-aging", billing).getBody();
        assertThat(report.get("buckets")).isNotNull();
        return ((List<Map<String, Object>>) report.get("rows")).stream()
                .filter(r -> guarantorId.equals(r.get("guarantorId")))
                .findFirst().orElse(null);
    }

    private double num(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).doubleValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void agingBucketsRollUpToGuarantorWithFifoPayments() {
        String parent = newPatient("Parent", "555-0177");
        String child = newPatient("Child", null);
        assertThat(api.put("/api/v1/patients/" + child + "/guarantor", billing,
                Map.of("guarantorPatientId", parent)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        backdatedCharge(parent, 50, 100);  // 90+ bucket, oldest
        backdatedCharge(child, 200, 75);   // 61-90 bucket
        backdatedCharge(parent, 100, 45);  // 31-60 bucket
        backdatedCharge(child, 80, 0);     // current
        // FIFO: this payment retires the oldest (90+) charge entirely
        pay(parent, 50);

        Map<String, Object> row = agingRowFor(parent);
        assertThat(row).isNotNull();
        assertThat((String) row.get("guarantorName")).contains("Parent");
        assertThat(row.get("phone")).isEqualTo("555-0177");
        assertThat(num(row, "current")).isEqualTo(80.0);
        assertThat(num(row, "days30")).isEqualTo(100.0);
        assertThat(num(row, "days60")).isEqualTo(200.0);
        assertThat(num(row, "days90plus")).isEqualTo(0.0);
        assertThat(num(row, "total")).isEqualTo(380.0);
        assertThat(row.get("lastPaymentDate")).isNotNull();

        // the dependent never gets their own row — the debt belongs to the account
        assertThat(agingRowFor(child)).isNull();

        // top-level buckets cover at least this family's debt
        Map<String, Object> buckets = (Map<String, Object>)
                api.get("/api/v1/reports/ar-aging", admin).getBody().get("buckets");
        assertThat(num(buckets, "total")).isGreaterThanOrEqualTo(380.0);
        assertThat(num(buckets, "days60")).isGreaterThanOrEqualTo(200.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectionsListsOverdueAccountsAndDropsThePaidOnes() {
        String account = newPatient("Overdue", "555-0188");
        backdatedCharge(account, 300, 50);

        List<Map<String, Object>> rows =
                api.getList("/api/v1/reports/collections", frontDesk).getBody();
        Map<String, Object> row = rows.stream()
                .filter(r -> account.equals(r.get("guarantorId")))
                .findFirst().orElseThrow();
        assertThat(num(row, "totalOverdue")).isEqualTo(300.0);
        assertThat(row.get("phone")).isEqualTo("555-0188");
        assertThat(row.get("oldestChargeDate"))
                .isEqualTo(LocalDate.now().minusDays(50).toString());
        assertThat(row.get("lastPaymentDate")).isNull();

        // rows come worst-first
        List<Double> overdueAmounts = rows.stream()
                .map(r -> ((Number) r.get("totalOverdue")).doubleValue())
                .toList();
        assertThat(overdueAmounts)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());

        // paying the account off removes it from collections and from aging
        pay(account, 300);
        assertThat(api.getList("/api/v1/reports/collections", billing).getBody())
                .noneMatch(r -> account.equals(r.get("guarantorId")));
        assertThat(agingRowFor(account)).isNull();
    }

    @Test
    void rbacIsEnforced() {
        // A/R aging is for billing roles only
        assertThat(api.get("/api/v1/reports/ar-aging", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/reports/ar-aging", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/reports/ar-aging", billing).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // collections includes the front desk, but not READ_ONLY
        assertThat(api.getList("/api/v1/reports/collections", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(api.get("/api/v1/reports/collections", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
