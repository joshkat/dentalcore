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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duplicate detection and patient merge (Phase C). Seeds rows in every table
 * that references patients(id) — through the public API where one exists, by
 * direct SQL where seeding through the API would drag in half the product —
 * and verifies the merge re-points all of them in one transaction.
 */
class PatientMergeIntegrationTest extends IntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(2_700_000_000L);
    private static final UUID CLINIC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String ADMIN_EMAIL = "admin-merge@clinic.test";
    private static final String FRONT_EMAIL = "front-merge@clinic.test";
    private static final String READONLY_EMAIL = "readonly-merge@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private HttpHeaders admin;
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);
        adminUserId = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getId();
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String uniquePhone() {
        return "555-" + SEQ.incrementAndGet();
    }

    private String uniqueEmail() {
        return "merge" + SEQ.incrementAndGet() + "@merge.test";
    }

    private String createPatient(String first, String last, String dob,
                                 String email, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", first);
        body.put("lastName", last);
        body.put("dateOfBirth", dob);
        body.put("sex", "FEMALE");
        if (email != null) {
            body.put("email", email);
        }
        if (phone != null) {
            body.put("phones", List.of(
                    Map.of("type", "MOBILE", "number", phone, "primary", true)));
        }
        ResponseEntity<Map<String, Object>> response =
                api.post("/api/v1/patients", admin, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private List<Map<String, Object>> duplicates() {
        ResponseEntity<List<Map<String, Object>>> response =
                api.getList("/api/v1/patients/duplicates", admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findPair(List<Map<String, Object>> dups,
                                                   String id1, String id2) {
        return dups.stream().filter(d -> {
            String a = (String) ((Map<String, Object>) d.get("first")).get("patientId");
            String b = (String) ((Map<String, Object>) d.get("second")).get("patientId");
            return (a.equals(id1) && b.equals(id2)) || (a.equals(id2) && b.equals(id1));
        }).findFirst();
    }

    @SuppressWarnings("unchecked")
    private boolean anyPairInvolving(List<Map<String, Object>> dups, String id) {
        return dups.stream().anyMatch(d ->
                id.equals(((Map<String, Object>) d.get("first")).get("patientId"))
                        || id.equals(((Map<String, Object>) d.get("second")).get("patientId")));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private ResponseEntity<Map<String, Object>> merge(HttpHeaders as, String targetId,
                                                      String sourceId) {
        return api.post("/api/v1/patients/" + targetId + "/merge", as,
                Map.of("sourceId", sourceId));
    }

    // ------------------------------------------------------------------
    // duplicate detection
    // ------------------------------------------------------------------

    @Test
    void duplicatesFindsDobNamePairAndPhonePairWithReasonsAndScores() {
        // same DOB + identical name, but distinct email/phone
        String a1 = createPatient("Dupcheck", "Samedob", "1971-03-03", uniqueEmail(), uniquePhone());
        String a2 = createPatient("Dupcheck", "Samedob", "1971-03-03", uniqueEmail(), uniquePhone());
        // same primary phone, but dissimilar names and different DOBs/emails
        String sharedPhone = uniquePhone();
        String b1 = createPatient("Quentin", "Zarkov", "1980-01-01", uniqueEmail(), sharedPhone);
        String b2 = createPatient("Beatrice", "Mulligan", "1981-02-02", uniqueEmail(), sharedPhone);

        List<Map<String, Object>> dups = duplicates();

        Map<String, Object> dobPair = findPair(dups, a1, a2).orElseThrow(
                () -> new AssertionError("expected same-dob/similar-name pair in duplicates"));
        assertThat((List<String>) dobPair.get("reasons"))
                .contains("SAME_DOB", "SIMILAR_NAME")
                .doesNotContain("SAME_PHONE", "SAME_EMAIL");
        assertThat(((Number) dobPair.get("score")).doubleValue()).isEqualTo(1.0);

        Map<String, Object> phonePair = findPair(dups, b1, b2).orElseThrow(
                () -> new AssertionError("expected exact-phone pair in duplicates"));
        assertThat((List<String>) phonePair.get("reasons"))
                .containsExactly("SAME_PHONE");
        assertThat(((Number) phonePair.get("score")).doubleValue()).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) dobPair.get("first");
        assertThat(first.get("name")).isEqualTo("Samedob, Dupcheck");
        assertThat(first.get("dateOfBirth")).isEqualTo("1971-03-03");
        assertThat(first.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void duplicatesExcludesMergedPatients() {
        String sharedPhone = uniquePhone();
        String c1 = createPatient("Xerxes", "Quibble", "1969-06-09", uniqueEmail(), sharedPhone);
        String c2 = createPatient("Wilhelmina", "Drosselmeyer", "1968-05-08", uniqueEmail(), sharedPhone);
        assertThat(findPair(duplicates(), c1, c2)).isPresent();

        assertThat(merge(admin, c1, c2).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> after = duplicates();
        assertThat(findPair(after, c1, c2)).isEmpty();
        assertThat(anyPairInvolving(after, c2)).isFalse();
    }

    // ------------------------------------------------------------------
    // merge
    // ------------------------------------------------------------------

    @Test
    void mergeRepointsEveryReferencingTableAndArchivesSource() {
        String sourceIdStr = createPatient("Mergesrc", "Duplicate", "1975-07-15",
                uniqueEmail(), uniquePhone());
        String targetIdStr = createPatient("Mergetgt", "Survivor", "1975-07-16",
                uniqueEmail(), uniquePhone());
        UUID source = UUID.fromString(sourceIdStr);
        UUID target = UUID.fromString(targetIdStr);

        // family link (created bidirectionally) and guarantor reference
        String relativeId = createPatient("Relative", "Ofmerge", "2005-01-01", null, null);
        assertThat(api.post("/api/v1/patients/" + sourceIdStr + "/family", admin,
                Map.of("relatedPatientId", relativeId, "relationship", "SIBLING"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String dependentId = createPatient("Dependent", "Ofmerge", "2010-02-02", null, null);
        assertThat(api.put("/api/v1/patients/" + dependentId + "/guarantor", admin,
                Map.of("guarantorPatientId", sourceIdStr)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // medical alert via API
        assertThat(api.post("/api/v1/patients/" + sourceIdStr + "/alerts", admin,
                Map.of("type", "ALLERGY", "description", "Penicillin", "severity", "HIGH"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // insurance: source has coverage on plan A (moves) and on plan B
        // (target already carries plan B -> unique-collision -> skipped)
        String carrierId = (String) api.post("/api/v1/insurance/carriers", admin,
                Map.of("name", "Merge Carrier " + SEQ.incrementAndGet()))
                .getBody().get("id");
        String planA = (String) api.post("/api/v1/insurance/plans", admin,
                Map.of("carrierId", carrierId, "planName", "Merge Plan A " + SEQ.incrementAndGet(),
                        "planType", "PPO")).getBody().get("id");
        String planB = (String) api.post("/api/v1/insurance/plans", admin,
                Map.of("carrierId", carrierId, "planName", "Merge Plan B " + SEQ.incrementAndGet(),
                        "planType", "PPO")).getBody().get("id");
        String coverageA = (String) api.post("/api/v1/patient-insurance", admin,
                coverage(sourceIdStr, planA, "PRIMARY")).getBody().get("id");
        assertThat(api.post("/api/v1/patient-insurance", admin,
                coverage(sourceIdStr, planB, "SECONDARY")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(api.post("/api/v1/patient-insurance", admin,
                coverage(targetIdStr, planB, "PRIMARY")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // rows that are cheaper to seed directly than through six other APIs
        UUID providerId = UUID.randomUUID();
        jdbc.update("INSERT INTO providers (id, clinic_id, type, first_name, last_name, npi)"
                        + " VALUES (?, ?, 'DENTIST', 'Merge', 'Provider', ?)",
                providerId, CLINIC, String.valueOf(SEQ.incrementAndGet()));
        UUID operatoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO operatories (id, clinic_id, name) VALUES (?, ?, ?)",
                operatoryId, CLINIC, "Merge Op " + SEQ.incrementAndGet());

        OffsetDateTime start = OffsetDateTime.parse("2033-04-01T09:00:00Z")
                .plusDays(SEQ.incrementAndGet() % 365);
        jdbc.update("INSERT INTO appointments (id, clinic_id, patient_id, provider_id,"
                        + " operatory_id, starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), CLINIC, source, providerId, operatoryId,
                start, start.plusMinutes(30));
        jdbc.update("INSERT INTO ledger_entries (id, clinic_id, patient_id, type, amount,"
                        + " description) VALUES (?, ?, ?, 'CHARGE', ?, 'Merge test charge')",
                UUID.randomUUID(), CLINIC, source, new BigDecimal("100.00"));
        jdbc.update("INSERT INTO claims (id, patient_insurance_id, patient_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.fromString(coverageA), source);
        jdbc.update("INSERT INTO clinical_notes (id, clinic_id, patient_id, author_user_id, body)"
                        + " VALUES (?, ?, ?, ?, 'merge note')",
                UUID.randomUUID(), CLINIC, source, adminUserId);
        jdbc.update("INSERT INTO treatment_plans (id, clinic_id, patient_id, provider_id, title)"
                        + " VALUES (?, ?, ?, ?, 'Merge plan')",
                UUID.randomUUID(), CLINIC, source, providerId);
        UUID codeId = jdbc.queryForObject("SELECT id FROM procedure_codes LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO completed_procedures (id, clinic_id, patient_id, provider_id,"
                        + " procedure_code_id, fee, completed_at, entry_date)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), CLINIC, source, providerId, codeId,
                new BigDecimal("50.00"), OffsetDateTime.now(), LocalDate.now());
        jdbc.update("INSERT INTO documents (id, clinic_id, patient_id, filename, content_type,"
                        + " size_bytes, storage_key) VALUES (?, ?, ?, 'merge.pdf',"
                        + " 'application/pdf', 123, ?)",
                UUID.randomUUID(), CLINIC, source, "merge-test-" + UUID.randomUUID());
        jdbc.update("INSERT INTO tooth_conditions (id, patient_id, tooth, condition)"
                        + " VALUES (?, ?, '11', 'CARIES')",
                UUID.randomUUID(), source);
        jdbc.update("INSERT INTO perio_exams (id, clinic_id, patient_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), CLINIC, source);
        UUID templateId = UUID.randomUUID();
        jdbc.update("INSERT INTO form_templates (id, clinic_id, name, fields)"
                        + " VALUES (?, ?, ?, '[]'::jsonb)",
                templateId, CLINIC, "Merge Intake " + SEQ.incrementAndGet());
        jdbc.update("INSERT INTO form_instances (id, clinic_id, template_id, patient_id)"
                        + " VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), CLINIC, templateId, source);
        jdbc.update("INSERT INTO reminders (id, patient_id, type, channel, status)"
                        + " VALUES (?, ?, 'RECALL', 'EMAIL', 'SENT')",
                UUID.randomUUID(), source);
        jdbc.update("INSERT INTO payment_plans (id, clinic_id, patient_id, total_amount,"
                        + " installment_amount, frequency, first_due_date)"
                        + " VALUES (?, ?, ?, ?, ?, 'MONTHLY', ?)",
                UUID.randomUUID(), CLINIC, source, new BigDecimal("100.00"),
                new BigDecimal("50.00"), LocalDate.now().plusDays(10));

        // ---- merge ----
        ResponseEntity<Map<String, Object>> response = merge(admin, targetIdStr, sourceIdStr);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("targetId")).isEqualTo(targetIdStr);
        assertThat(response.getBody().get("sourceId")).isEqualTo(sourceIdStr);

        @SuppressWarnings("unchecked")
        Map<String, Object> repointed = (Map<String, Object>) response.getBody().get("repointed");
        assertThat(repointed)
                .containsEntry("appointments", 1)
                .containsEntry("ledger_entries", 1)
                .containsEntry("claims", 1)
                // plan A patient_id + both source rows' subscriber_patient_id
                .containsEntry("patient_insurance", 3)
                .containsEntry("clinical_notes", 1)
                .containsEntry("treatment_plans", 1)
                .containsEntry("completed_procedures", 1)
                .containsEntry("documents", 1)
                .containsEntry("tooth_conditions", 1)
                .containsEntry("perio_exams", 1)
                .containsEntry("medical_alerts", 1)
                // one link in each direction
                .containsEntry("family_links", 2)
                .containsEntry("form_instances", 1)
                .containsEntry("reminders", 1)
                .containsEntry("guarantor_refs", 1)
                .containsEntry("payment_plans", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> skipped = (Map<String, Object>) response.getBody().get("skipped");
        assertThat(skipped).containsExactlyEntriesOf(Map.of("patient_insurance", 1));

        // ---- the database agrees ----
        for (String table : List.of("appointments", "ledger_entries", "claims",
                "clinical_notes", "treatment_plans", "completed_procedures", "documents",
                "tooth_conditions", "perio_exams", "medical_alerts", "form_instances",
                "reminders", "payment_plans")) {
            assertThat(count("SELECT count(*) FROM " + table + " WHERE patient_id = ?", source))
                    .as("%s rows left on source", table).isZero();
            assertThat(count("SELECT count(*) FROM " + table + " WHERE patient_id = ?", target))
                    .as("%s rows on target", table).isEqualTo(1);
        }
        // skipped coverage stays on the source; the moved one is on the target
        assertThat(count("SELECT count(*) FROM patient_insurance WHERE patient_id = ?", source))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM patient_insurance WHERE patient_id = ?", target))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM patient_insurance"
                + " WHERE subscriber_patient_id = ?", source)).isZero();
        // family links re-pointed in both directions, none left on the source
        UUID relative = UUID.fromString(relativeId);
        assertThat(count("SELECT count(*) FROM family_links WHERE patient_id = ?"
                + " AND related_patient_id = ?", target, relative)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM family_links WHERE patient_id = ?"
                + " AND related_patient_id = ?", relative, target)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM family_links WHERE patient_id = ?"
                + " OR related_patient_id = ?", source, source)).isZero();
        // guarantor reference now points at the target
        assertThat(jdbc.queryForObject("SELECT guarantor_id FROM patients WHERE id = ?",
                UUID.class, UUID.fromString(dependentId))).isEqualTo(target);

        // ---- tombstone + target untouched, history still readable ----
        Map<String, Object> sourceAfter =
                api.get("/api/v1/patients/" + sourceIdStr, admin).getBody();
        assertThat(sourceAfter.get("status")).isEqualTo("ARCHIVED");
        assertThat(sourceAfter.get("mergedIntoPatientId")).isEqualTo(targetIdStr);
        Map<String, Object> targetAfter =
                api.get("/api/v1/patients/" + targetIdStr, admin).getBody();
        assertThat(targetAfter.get("status")).isEqualTo("ACTIVE");
        assertThat(targetAfter.get("firstName")).isEqualTo("Mergetgt");
        assertThat(targetAfter.get("mergedIntoPatientId")).isNull();

        // ---- audit event with the repointed summary ----
        ResponseEntity<List<Map<String, Object>>> timeline =
                api.getList("/api/v1/patients/" + targetIdStr + "/timeline", admin);
        assertThat(timeline.getBody()).anySatisfy(event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> newValue = (Map<String, Object>) event.get("newValue");
            assertThat(newValue).isNotNull();
            assertThat(newValue.get("sourceId")).isEqualTo(sourceIdStr);
            assertThat((Map<String, Object>) newValue.get("repointed"))
                    .containsEntry("appointments", 1);
        });
    }

    private Map<String, Object> coverage(String patientId, String planId, String priority) {
        return Map.of(
                "patientId", patientId,
                "planId", planId,
                "subscriberPatientId", patientId,
                "relationshipToSubscriber", "SELF",
                "memberId", "M" + SEQ.incrementAndGet(),
                "priority", priority);
    }

    // ------------------------------------------------------------------
    // validation + security
    // ------------------------------------------------------------------

    @Test
    void mergeValidationRules() {
        String t = createPatient("Validation", "Targetone", "1990-01-01", null, null);
        String s = createPatient("Validation", "Sourceone", "1991-01-01", null, null);

        assertThat(merge(admin, t, t).getStatusCode())
                .as("self merge").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(merge(admin, UUID.randomUUID().toString(), s).getStatusCode())
                .as("unknown target").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(merge(admin, t, UUID.randomUUID().toString()).getStatusCode())
                .as("unknown source").isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(merge(admin, t, s).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merge(admin, t, s).getStatusCode())
                .as("double merge of the same source").isEqualTo(HttpStatus.BAD_REQUEST);

        String x = createPatient("Validation", "Extraone", "1992-01-01", null, null);
        assertThat(merge(admin, s, x).getStatusCode())
                .as("merge into an already-merged target").isEqualTo(HttpStatus.BAD_REQUEST);

        String archived = createPatient("Validation", "Archivedone", "1993-01-01", null, null);
        assertThat(api.patch("/api/v1/patients/" + archived + "/status", admin,
                Map.of("status", "ARCHIVED")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merge(admin, archived, x).getStatusCode())
                .as("merge into an archived target").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicatesAndMergeRequireAdmin() {
        String t = createPatient("Roles", "Targettwo", "1990-02-02", null, null);
        String s = createPatient("Roles", "Sourcetwo", "1991-02-02", null, null);

        assertThat(api.get("/api/v1/patients/duplicates", frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/patients/duplicates", readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(merge(frontDesk, t, s).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(merge(readOnly, t, s).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // and the patients are untouched
        assertThat(api.get("/api/v1/patients/" + s, admin).getBody().get("status"))
                .isEqualTo("ACTIVE");
    }
}
