package com.dentalcore.reporting.internal.service;

import com.dentalcore.reporting.internal.dto.ReportDtos.DailyProductionReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.DailyProductionRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.DashboardSummary;
import com.dentalcore.reporting.internal.dto.ReportDtos.PatientGrowthRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderAppointmentsRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderUtilizationRow;
import com.dentalcore.shared.error.InvalidRequestException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read models over the live schema. Reporting deliberately queries with SQL
 * instead of other modules' services: reports span modules, are read-only,
 * and must not create module dependencies.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private static final java.util.UUID DEFAULT_CLINIC_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final NamedParameterJdbcTemplate jdbc;
    private final com.dentalcore.infrastructure.time.ClinicTimeService clinicTime;

    public ReportingService(NamedParameterJdbcTemplate jdbc,
                            com.dentalcore.infrastructure.time.ClinicTimeService clinicTime) {
        this.jdbc = jdbc;
        this.clinicTime = clinicTime;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'to' must not precede 'from'");
        }
        if (from.plusYears(3).isBefore(to)) {
            throw new InvalidRequestException("Range too large (max 3 years)");
        }
    }

    public List<ProviderAppointmentsRow> appointmentsByProvider(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String sql = """
                SELECT a.provider_id,
                       pr.last_name || ', ' || pr.first_name AS provider_name,
                       COUNT(*) FILTER (WHERE a.status = 'SCHEDULED')   AS scheduled,
                       COUNT(*) FILTER (WHERE a.status = 'CONFIRMED')   AS confirmed,
                       COUNT(*) FILTER (WHERE a.status = 'CHECKED_IN')  AS checked_in,
                       COUNT(*) FILTER (WHERE a.status = 'IN_PROGRESS') AS in_progress,
                       COUNT(*) FILTER (WHERE a.status = 'COMPLETED')   AS completed,
                       COUNT(*) FILTER (WHERE a.status = 'NO_SHOW')     AS no_show,
                       COUNT(*) FILTER (WHERE a.status = 'CANCELLED')   AS cancelled,
                       COUNT(*)                                          AS total
                FROM appointments a
                JOIN providers pr ON pr.id = a.provider_id
                WHERE a.deleted_at IS NULL
                  AND a.starts_at >= :from AND a.starts_at < :to
                GROUP BY a.provider_id, provider_name
                ORDER BY total DESC
                """;
        return jdbc.query(sql, rangeParams(from, to), (rs, i) -> new ProviderAppointmentsRow(
                rs.getString("provider_id"), rs.getString("provider_name"),
                rs.getLong("scheduled"), rs.getLong("confirmed"), rs.getLong("checked_in"),
                rs.getLong("in_progress"), rs.getLong("completed"), rs.getLong("no_show"),
                rs.getLong("cancelled"), rs.getLong("total")));
    }

    public DailyProductionReport dailyProduction(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String sql = """
                SELECT e.entry_date,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.type = 'CHARGE'), 0)            AS charges,
                       COALESCE(-SUM(e.amount) FILTER (WHERE e.type = 'PAYMENT'), 0)          AS patient_payments,
                       COALESCE(-SUM(e.amount) FILTER (WHERE e.type = 'INSURANCE_PAYMENT'), 0) AS insurance_payments,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.type = 'ADJUSTMENT'), 0)        AS adjustments,
                       COALESCE(SUM(e.amount), 0)                                              AS net
                FROM ledger_entries e
                WHERE e.entry_date BETWEEN :from AND :to
                GROUP BY e.entry_date
                ORDER BY e.entry_date
                """;
        List<DailyProductionRow> days = jdbc.query(sql,
                Map.of("from", from, "to", to),
                (rs, i) -> new DailyProductionRow(
                        rs.getObject("entry_date", LocalDate.class),
                        rs.getBigDecimal("charges"),
                        rs.getBigDecimal("patient_payments"),
                        rs.getBigDecimal("insurance_payments"),
                        rs.getBigDecimal("adjustments"),
                        rs.getBigDecimal("net")));
        return new DailyProductionReport(
                days,
                sum(days, DailyProductionRow::charges),
                sum(days, DailyProductionRow::patientPayments),
                sum(days, DailyProductionRow::insurancePayments),
                sum(days, DailyProductionRow::adjustments),
                sum(days, DailyProductionRow::net));
    }

    public List<PatientGrowthRow> patientGrowth(int months) {
        int window = Math.min(Math.max(months, 1), 60);
        String sql = """
                WITH monthly AS (
                    SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month,
                           date_trunc('month', created_at)                      AS month_start,
                           COUNT(*)                                             AS new_patients
                    FROM patients
                    WHERE deleted_at IS NULL
                      AND created_at >= date_trunc('month', now()) - make_interval(months => :window - 1)
                    GROUP BY 1, 2
                )
                SELECT month, new_patients,
                       SUM(new_patients) OVER (ORDER BY month_start) +
                       (SELECT COUNT(*) FROM patients
                        WHERE deleted_at IS NULL
                          AND created_at < date_trunc('month', now()) - make_interval(months => :window - 1))
                       AS cumulative
                FROM monthly
                ORDER BY month
                """;
        return jdbc.query(sql, Map.of("window", window),
                (rs, i) -> new PatientGrowthRow(
                        rs.getString("month"), rs.getLong("new_patients"),
                        rs.getLong("cumulative")));
    }

    public List<ProviderUtilizationRow> providerUtilization(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String sql = """
                SELECT a.provider_id,
                       pr.last_name || ', ' || pr.first_name AS provider_name,
                       COUNT(*) AS appointments,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (a.ends_at - a.starts_at)) / 60), 0)::bigint
                           AS booked_minutes,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (a.ends_at - a.starts_at)) / 60)
                           FILTER (WHERE a.status = 'COMPLETED'), 0)::bigint AS completed_minutes,
                       COUNT(DISTINCT a.patient_id) AS distinct_patients
                FROM appointments a
                JOIN providers pr ON pr.id = a.provider_id
                WHERE a.deleted_at IS NULL
                  AND a.status NOT IN ('CANCELLED')
                  AND a.starts_at >= :from AND a.starts_at < :to
                GROUP BY a.provider_id, provider_name
                ORDER BY booked_minutes DESC
                """;
        return jdbc.query(sql, rangeParams(from, to), (rs, i) -> new ProviderUtilizationRow(
                rs.getString("provider_id"), rs.getString("provider_name"),
                rs.getLong("appointments"), rs.getLong("booked_minutes"),
                rs.getLong("completed_minutes"), rs.getLong("distinct_patients")));
    }

    public DashboardSummary dashboard() {
        // "today" follows the clinic's timezone, not the server clock
        java.time.ZonedDateTime dayStart = clinicTime.startOfToday(DEFAULT_CLINIC_ID);
        LocalDate today = clinicTime.today(DEFAULT_CLINIC_ID);

        Long activePatients = jdbc.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE deleted_at IS NULL AND status = 'ACTIVE'",
                Map.of(), Long.class);
        Map<String, Object> appts = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed
                FROM appointments
                WHERE deleted_at IS NULL
                  AND starts_at >= :dayStart
                  AND starts_at < :dayEnd
                """, Map.of(
                "dayStart", dayStart.toOffsetDateTime(),
                "dayEnd", dayStart.plusDays(1).toOffsetDateTime()));
        Map<String, Object> money = jdbc.queryForMap("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE type = 'CHARGE'), 0) AS production,
                       COALESCE(-SUM(amount) FILTER (WHERE type IN ('PAYMENT', 'INSURANCE_PAYMENT')), 0)
                           AS collections
                FROM ledger_entries
                WHERE entry_date = :today
                """, Map.of("today", today));
        Long openClaims = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claims WHERE status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'DENIED')",
                Map.of(), Long.class);

        return new DashboardSummary(
                activePatients == null ? 0 : activePatients,
                ((Number) appts.get("total")).longValue(),
                ((Number) appts.get("completed")).longValue(),
                (BigDecimal) money.get("production"),
                (BigDecimal) money.get("collections"),
                openClaims == null ? 0 : openClaims);
    }

    // ---- helpers ----

    private Map<String, Object> rangeParams(LocalDate from, LocalDate to) {
        // timestamps: [from 00:00, to+1d 00:00) so 'to' is inclusive as a date
        return Map.of(
                "from", from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
                "to", to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC));
    }

    private BigDecimal sum(List<DailyProductionRow> rows,
                           java.util.function.Function<DailyProductionRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
