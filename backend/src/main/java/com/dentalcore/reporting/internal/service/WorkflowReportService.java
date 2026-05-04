package com.dentalcore.reporting.internal.service;

import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.reporting.internal.dto.ReportDtos.AsapRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.DaySheetEntry;
import com.dentalcore.reporting.internal.dto.ReportDtos.DaySheetProviderRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.DaySheetReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.DaySheetTotals;
import com.dentalcore.reporting.internal.dto.ReportDtos.DepositSlipRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.UnscheduledTreatmentRow;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Front-desk workflow read models (day sheet, unscheduled treatment, ASAP
 * list). Like {@link ReportingService}, these query the live schema with SQL:
 * they span modules, are read-only, and must not create module dependencies.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowReportService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicTimeService clinicTime;

    public WorkflowReportService(NamedParameterJdbcTemplate jdbc, ClinicTimeService clinicTime) {
        this.jdbc = jdbc;
        this.clinicTime = clinicTime;
    }

    /**
     * End-of-day reconciliation for one business date. Reversals count against
     * the bucket of the entry they void (a reversed charge nets out of
     * production); pure adjustments form their own bucket. Charges are
     * attributed to providers through the completed procedure that posted
     * them; payments carry no provider, so they roll up on the unattributed
     * row.
     */
    public DaySheetReport daySheet(LocalDate date) {
        LocalDate effective = date != null ? date : clinicTime.today(DEFAULT_CLINIC_ID);
        Map<String, Object> params = Map.of("date", effective);

        List<DaySheetEntry> entries = jdbc.query("""
                SELECT e.id AS entry_id,
                       e.created_at AS occurred_at,
                       e.patient_id,
                       p.last_name || ', ' || p.first_name AS patient_name,
                       pr.last_name || ', ' || pr.first_name AS provider_name,
                       CASE WHEN e.reversal_of IS NOT NULL THEN 'REVERSAL'
                            WHEN e.type = 'INSURANCE_PAYMENT' THEN 'PAYMENT'
                            ELSE e.type END AS entry_type,
                       e.description,
                       e.amount
                FROM ledger_entries e
                JOIN patients p ON p.id = e.patient_id
                LEFT JOIN completed_procedures cp ON cp.ledger_entry_id = e.id
                LEFT JOIN providers pr ON pr.id = cp.provider_id
                WHERE e.entry_date = :date
                ORDER BY e.created_at
                """, params, (rs, i) -> new DaySheetEntry(
                rs.getString("entry_id"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("patient_id"),
                rs.getString("patient_name"),
                rs.getString("provider_name"),
                rs.getString("entry_type"),
                rs.getString("description"),
                rs.getBigDecimal("amount")));

        Map<String, Object> totalsRow = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(e.amount) FILTER (
                      WHERE e.type = 'CHARGE' OR orig.type = 'CHARGE'), 0) AS production,
                  COALESCE(-SUM(e.amount) FILTER (
                      WHERE e.type IN ('PAYMENT', 'INSURANCE_PAYMENT')
                         OR orig.type IN ('PAYMENT', 'INSURANCE_PAYMENT')), 0) AS collections,
                  COALESCE(SUM(e.amount) FILTER (
                      WHERE (e.type = 'ADJUSTMENT' AND e.reversal_of IS NULL)
                         OR orig.type = 'ADJUSTMENT'), 0) AS adjustments
                FROM ledger_entries e
                LEFT JOIN ledger_entries orig ON orig.id = e.reversal_of
                WHERE e.entry_date = :date
                """, params);
        DaySheetTotals totals = new DaySheetTotals(
                (BigDecimal) totalsRow.get("production"),
                (BigDecimal) totalsRow.get("collections"),
                (BigDecimal) totalsRow.get("adjustments"));

        List<DaySheetProviderRow> providers = new ArrayList<>(jdbc.query("""
                SELECT cp.provider_id,
                       pr.last_name || ', ' || pr.first_name AS provider_name,
                       COALESCE(SUM(e.amount), 0) AS production
                FROM ledger_entries e
                JOIN completed_procedures cp ON cp.ledger_entry_id = e.id
                JOIN providers pr ON pr.id = cp.provider_id
                WHERE e.entry_date = :date AND e.type = 'CHARGE'
                GROUP BY cp.provider_id, provider_name
                ORDER BY production DESC
                """, params, (rs, i) -> new DaySheetProviderRow(
                rs.getString("provider_id"),
                rs.getString("provider_name"),
                rs.getBigDecimal("production"),
                BigDecimal.ZERO)));
        BigDecimal attributed = providers.stream()
                .map(DaySheetProviderRow::production)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unattributed = totals.production().subtract(attributed);
        if (unattributed.signum() != 0 || totals.collections().signum() != 0) {
            providers.add(new DaySheetProviderRow(
                    null, null, unattributed, totals.collections()));
        }

        List<DepositSlipRow> depositSlip = jdbc.query("""
                SELECT e.method, COUNT(*) AS cnt, COALESCE(-SUM(e.amount), 0) AS total
                FROM ledger_entries e
                WHERE e.entry_date = :date AND e.type = 'PAYMENT'
                GROUP BY e.method
                ORDER BY e.method
                """, params, (rs, i) -> new DepositSlipRow(
                rs.getString("method"),
                rs.getLong("cnt"),
                rs.getBigDecimal("total")));

        return new DaySheetReport(effective, providers, entries, totals, depositSlip);
    }

    /**
     * Patients with approved/in-progress plans that still have PLANNED work
     * and no upcoming visit on the books — the follow-up call list.
     */
    public List<UnscheduledTreatmentRow> unscheduledTreatment() {
        return jdbc.query("""
                SELECT tp.patient_id,
                       p.last_name || ', ' || p.first_name AS patient_name,
                       ph.number AS phone,
                       tp.id AS plan_id,
                       tp.title AS plan_title,
                       tp.status AS plan_status,
                       COUNT(pp.id) FILTER (WHERE pp.status = 'PLANNED') AS planned_count,
                       COALESCE(SUM(pp.estimated_cost)
                           FILTER (WHERE pp.status = 'PLANNED'), 0) AS remaining_value,
                       p.next_recall_date
                FROM treatment_plans tp
                JOIN patients p ON p.id = tp.patient_id
                JOIN planned_procedures pp ON pp.treatment_plan_id = tp.id
                LEFT JOIN LATERAL (
                    SELECT number FROM patient_phones
                    WHERE patient_id = p.id
                    ORDER BY is_primary DESC, created_at
                    LIMIT 1
                ) ph ON true
                WHERE tp.deleted_at IS NULL
                  AND p.deleted_at IS NULL
                  AND tp.status IN ('APPROVED', 'IN_PROGRESS')
                  AND NOT EXISTS (
                      SELECT 1 FROM appointments a
                      WHERE a.patient_id = tp.patient_id
                        AND a.deleted_at IS NULL
                        AND a.status IN ('SCHEDULED', 'CONFIRMED', 'CHECKED_IN')
                        AND a.starts_at > now())
                GROUP BY tp.patient_id, patient_name, ph.number, tp.id, tp.title, tp.status,
                         p.next_recall_date
                HAVING COUNT(pp.id) FILTER (WHERE pp.status = 'PLANNED') >= 1
                ORDER BY remaining_value DESC
                """, Map.of(), (rs, i) -> new UnscheduledTreatmentRow(
                rs.getString("patient_id"),
                rs.getString("patient_name"),
                rs.getString("phone"),
                rs.getString("plan_id"),
                rs.getString("plan_title"),
                rs.getString("plan_status"),
                rs.getLong("planned_count"),
                rs.getBigDecimal("remaining_value"),
                rs.getObject("next_recall_date", LocalDate.class)));
    }

    /** Upcoming appointments flagged ASAP — candidates to fill freed slots. */
    public List<AsapRow> asapList() {
        return jdbc.query("""
                SELECT a.id AS appointment_id,
                       a.patient_id,
                       p.last_name || ', ' || p.first_name AS patient_name,
                       ph.number AS phone,
                       pr.last_name || ', ' || pr.first_name AS provider_name,
                       a.starts_at,
                       a.status
                FROM appointments a
                JOIN patients p ON p.id = a.patient_id
                JOIN providers pr ON pr.id = a.provider_id
                LEFT JOIN LATERAL (
                    SELECT number FROM patient_phones
                    WHERE patient_id = p.id
                    ORDER BY is_primary DESC, created_at
                    LIMIT 1
                ) ph ON true
                WHERE a.deleted_at IS NULL
                  AND a.asap = TRUE
                  AND a.status IN ('SCHEDULED', 'CONFIRMED')
                  AND a.starts_at > now()
                ORDER BY a.starts_at
                """, Map.of(), (rs, i) -> new AsapRow(
                rs.getString("appointment_id"),
                rs.getString("patient_id"),
                rs.getString("patient_name"),
                rs.getString("phone"),
                rs.getString("provider_name"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getString("status")));
    }
}
