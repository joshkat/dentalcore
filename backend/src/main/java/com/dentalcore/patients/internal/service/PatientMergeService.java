package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.internal.dto.PatientMergeDtos.DuplicateCandidateResponse;
import com.dentalcore.patients.internal.dto.PatientMergeDtos.DuplicatePatientRef;
import com.dentalcore.patients.internal.dto.PatientMergeDtos.MergeResponse;
import com.dentalcore.patients.internal.entity.Patient;
import com.dentalcore.patients.internal.entity.PatientStatus;
import com.dentalcore.patients.internal.repository.PatientRepository;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duplicate detection and patient merge.
 *
 * <p><b>Why raw SQL in a modular monolith:</b> merging a duplicate patient is
 * the sanctioned exception to per-module data ownership. The merge must
 * re-point every table that references {@code patients(id)} — a dozen-plus
 * tables owned by eight different modules — atomically, in one transaction.
 * Module APIs for bulk re-pointing do not exist, and adding twelve
 * single-purpose "re-point patient FK" APIs would couple every module to this
 * one feature and still not give us a single transaction boundary. Instead,
 * this class owns one documented, schema-aware SQL routine (via
 * {@link NamedParameterJdbcTemplate}). <b>Any new table that references
 * {@code patients(id)} must be added to {@link #merge}.</b>
 *
 * <p><b>Collision (skip) policy:</b> where re-pointing a row would collide
 * with something the target patient already has — e.g. live insurance
 * coverage on the same plan — the row is <i>skipped</i>: it stays on the
 * archived source record and is counted in the {@code skipped} map of the
 * response instead of failing the whole merge. Family links that would become
 * self-referencing or duplicates after re-pointing are dropped outright
 * (they carry no information once both sides are the same household).
 * {@code patient_phones} intentionally stay on the source: they are part of
 * the duplicate record's contact history, and the target keeps its own.
 */
@Service
public class PatientMergeService {

    static final String ENTITY_TYPE = "Patient";

    private static final double NAME_SIMILARITY_THRESHOLD = 0.6;
    private static final int MAX_CANDIDATES = 50;

    /**
     * Candidate pairs: ACTIVE, never-merged patients sharing a date of birth
     * with similar names (pg_trgm), or with the exact same primary phone or
     * email. {@code b.id > a.id} reports each unordered pair once. Ties are
     * broken by newest record first so fresh duplicates surface even when the
     * result is capped.
     */
    private static final String DUPLICATES_SQL = """
            SELECT * FROM (
                SELECT a.id AS a_id, a.first_name AS a_first, a.last_name AS a_last,
                       a.date_of_birth AS a_dob, a.status AS a_status,
                       b.id AS b_id, b.first_name AS b_first, b.last_name AS b_last,
                       b.date_of_birth AS b_dob, b.status AS b_status,
                       similarity(a.first_name || ' ' || a.last_name,
                                  b.first_name || ' ' || b.last_name) AS name_sim,
                       (a.date_of_birth = b.date_of_birth) AS same_dob,
                       EXISTS (SELECT 1 FROM patient_phones pa
                                 JOIN patient_phones pb ON pb.number = pa.number
                                WHERE pa.patient_id = a.id AND pb.patient_id = b.id
                                  AND pa.is_primary AND pb.is_primary) AS same_phone,
                       (a.email IS NOT NULL AND b.email IS NOT NULL
                           AND lower(a.email) = lower(b.email)) AS same_email,
                       GREATEST(a.created_at, b.created_at) AS newest
                  FROM patients a
                  JOIN patients b
                    ON b.id > a.id
                   AND b.status = 'ACTIVE'
                   AND b.merged_into_patient_id IS NULL
                   AND b.deleted_at IS NULL
                 WHERE a.status = 'ACTIVE'
                   AND a.merged_into_patient_id IS NULL
                   AND a.deleted_at IS NULL
            ) pair
            WHERE (same_dob AND name_sim > :threshold) OR same_phone OR same_email
            ORDER BY CASE WHEN same_phone OR same_email THEN 1.0 ELSE name_sim END DESC,
                     newest DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PatientRepository patientRepository;
    private final ApplicationEventPublisher events;

    public PatientMergeService(NamedParameterJdbcTemplate jdbc,
                               PatientRepository patientRepository,
                               ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.patientRepository = patientRepository;
        this.events = events;
    }

    // ------------------------------------------------------------------
    // Duplicate detection
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DuplicateCandidateResponse> findDuplicates() {
        return jdbc.query(
                DUPLICATES_SQL,
                Map.of("threshold", NAME_SIMILARITY_THRESHOLD, "limit", MAX_CANDIDATES),
                (rs, rowNum) -> {
                    double nameSimilarity = rs.getDouble("name_sim");
                    boolean sameDob = rs.getBoolean("same_dob");
                    boolean samePhone = rs.getBoolean("same_phone");
                    boolean sameEmail = rs.getBoolean("same_email");

                    List<String> reasons = new ArrayList<>();
                    if (sameDob) {
                        reasons.add("SAME_DOB");
                    }
                    if (nameSimilarity > NAME_SIMILARITY_THRESHOLD) {
                        reasons.add("SIMILAR_NAME");
                    }
                    if (samePhone) {
                        reasons.add("SAME_PHONE");
                    }
                    if (sameEmail) {
                        reasons.add("SAME_EMAIL");
                    }
                    double score = (samePhone || sameEmail) ? 1.0 : nameSimilarity;
                    return new DuplicateCandidateResponse(
                            ref(rs.getObject("a_id", UUID.class), rs.getString("a_last"),
                                    rs.getString("a_first"),
                                    rs.getObject("a_dob", java.time.LocalDate.class),
                                    rs.getString("a_status")),
                            ref(rs.getObject("b_id", UUID.class), rs.getString("b_last"),
                                    rs.getString("b_first"),
                                    rs.getObject("b_dob", java.time.LocalDate.class),
                                    rs.getString("b_status")),
                            score, reasons);
                });
    }

    private static DuplicatePatientRef ref(UUID id, String last, String first,
                                           java.time.LocalDate dob, String status) {
        return new DuplicatePatientRef(id, last + ", " + first, dob, status);
    }

    // ------------------------------------------------------------------
    // Merge
    // ------------------------------------------------------------------

    /**
     * Merges {@code sourceId} into {@code targetId}: re-points every row that
     * references the source patient, then archives the source with a
     * tombstone ({@code merged_into_patient_id}). One transaction — either
     * everything moves or nothing does. Counts in the returned map are
     * column re-points (a family link pair counts twice, once per direction;
     * an insurance row where the patient is also the subscriber counts once
     * per re-pointed column).
     */
    @Transactional
    public MergeResponse merge(UUID targetId, UUID sourceId) {
        if (targetId.equals(sourceId)) {
            throw new InvalidRequestException("A patient cannot be merged into themselves");
        }
        Patient target = findPatient(targetId);
        Patient source = findPatient(sourceId);
        if (source.getMergedIntoPatientId() != null) {
            throw new InvalidRequestException("Source patient has already been merged");
        }
        if (target.getMergedIntoPatientId() != null) {
            throw new InvalidRequestException("Target patient has already been merged away");
        }
        if (target.getStatus() == PatientStatus.ARCHIVED) {
            throw new InvalidRequestException("Target patient is archived");
        }

        Map<String, Object> params = Map.of("source", sourceId, "target", targetId);
        Map<String, Integer> repointed = new LinkedHashMap<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();

        repointed.put("appointments", repoint("appointments", params));
        repointed.put("ledger_entries", repoint("ledger_entries", params));
        repointed.put("claims", repoint("claims", params));
        repointed.put("patient_insurance", repointPatientInsurance(params, skipped));
        repointed.put("clinical_notes", repoint("clinical_notes", params));
        repointed.put("treatment_plans", repoint("treatment_plans", params));
        repointed.put("completed_procedures", repoint("completed_procedures", params));
        repointed.put("documents", repoint("documents", params));
        repointed.put("tooth_conditions", repoint("tooth_conditions", params));
        repointed.put("perio_exams", repoint("perio_exams", params));
        repointed.put("medical_alerts", repoint("medical_alerts", params));
        repointed.put("family_links", repointFamilyLinks(params));
        repointed.put("form_instances", repoint("form_instances", params));
        repointed.put("reminders", repoint("reminders", params));
        repointed.put("guarantor_refs", repointGuarantorRefs(params));
        // Not in the original contract list but references patients(id) (V15);
        // additive key so consumers that index by name are unaffected.
        repointed.put("payment_plans", repoint("payment_plans", params));

        // Tombstone: archive the source and point it at the survivor. Its own
        // guarantor link is cleared — a merged-away account rolls up nowhere.
        jdbc.update("""
                UPDATE patients
                   SET status = 'ARCHIVED',
                       merged_into_patient_id = :target,
                       guarantor_id = NULL,
                       updated_at = now()
                 WHERE id = :source
                """, params);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("merge", "Merged duplicate patient record");
        summary.put("sourceId", sourceId.toString());
        summary.put("repointed", repointed);
        summary.put("skipped", skipped);
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, targetId,
                AuditEvent.AuditAction.UPDATE,
                Map.of("mergedSourceId", sourceId.toString()),
                summary));

        return new MergeResponse(targetId, sourceId, repointed, skipped);
    }

    /** Plain re-point: the table has no unique constraint involving patient_id. */
    private int repoint(String table, Map<String, Object> params) {
        return jdbc.update("UPDATE " + table
                + " SET patient_id = :target, updated_at = now()"
                + " WHERE patient_id = :source", params);
    }

    /**
     * Coverage rows: there is no DB unique on (patient_id, plan_id), but two
     * live coverages on the same plan for one patient are a logical
     * duplicate. Live source rows whose plan the target already carries are
     * skipped (left on the source, counted under {@code skipped}); everything
     * else, plus all subscriber references, moves.
     */
    private int repointPatientInsurance(Map<String, Object> params, Map<String, Integer> skipped) {
        String collision = """
                s.deleted_at IS NULL AND EXISTS (
                    SELECT 1 FROM patient_insurance t
                     WHERE t.patient_id = :target
                       AND t.plan_id = s.plan_id
                       AND t.deleted_at IS NULL)
                """;
        Integer collisions = jdbc.queryForObject(
                "SELECT count(*) FROM patient_insurance s WHERE s.patient_id = :source AND "
                        + collision,
                params, Integer.class);
        if (collisions != null && collisions > 0) {
            skipped.put("patient_insurance", collisions);
        }
        int moved = jdbc.update("""
                UPDATE patient_insurance s
                   SET patient_id = :target, updated_at = now()
                 WHERE s.patient_id = :source AND NOT (
                """ + collision + ")", params);
        int subscriberMoved = jdbc.update("""
                UPDATE patient_insurance
                   SET subscriber_patient_id = :target, updated_at = now()
                 WHERE subscriber_patient_id = :source
                """, params);
        return moved + subscriberMoved;
    }

    /**
     * Family links reference patients on both columns and carry a
     * (patient_id, related_patient_id) unique plus a no-self-link check.
     * Links between source and target, and source links the target already
     * has, are deleted <i>before</i> re-pointing so the constraints cannot
     * fire mid-update.
     */
    private int repointFamilyLinks(Map<String, Object> params) {
        // would become self-links
        jdbc.update("""
                DELETE FROM family_links
                 WHERE (patient_id = :source AND related_patient_id = :target)
                    OR (patient_id = :target AND related_patient_id = :source)
                """, params);
        // would duplicate a link the target already owns (either direction)
        jdbc.update("""
                DELETE FROM family_links s
                 WHERE s.patient_id = :source AND EXISTS (
                       SELECT 1 FROM family_links t
                        WHERE t.patient_id = :target
                          AND t.related_patient_id = s.related_patient_id)
                """, params);
        jdbc.update("""
                DELETE FROM family_links s
                 WHERE s.related_patient_id = :source AND EXISTS (
                       SELECT 1 FROM family_links t
                        WHERE t.related_patient_id = :target
                          AND t.patient_id = s.patient_id)
                """, params);
        int outgoing = jdbc.update("""
                UPDATE family_links SET patient_id = :target, updated_at = now()
                 WHERE patient_id = :source
                """, params);
        int incoming = jdbc.update("""
                UPDATE family_links SET related_patient_id = :target, updated_at = now()
                 WHERE related_patient_id = :source
                """, params);
        return outgoing + incoming;
    }

    /**
     * patients.guarantor_id references the source. If the target itself was
     * guaranteed by the source, it becomes self-guaranteed (NULL by
     * convention); everyone else now rolls up to the target.
     */
    private int repointGuarantorRefs(Map<String, Object> params) {
        int selfCleared = jdbc.update("""
                UPDATE patients SET guarantor_id = NULL, updated_at = now()
                 WHERE id = :target AND guarantor_id = :source
                """, params);
        int moved = jdbc.update("""
                UPDATE patients SET guarantor_id = :target, updated_at = now()
                 WHERE guarantor_id = :source AND id <> :target
                """, params);
        return selfCleared + moved;
    }

    private Patient findPatient(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }
}
