package com.dentalcore.reporting.internal.service;

import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.reporting.internal.dto.ReportDtos.ArAgingBuckets;
import com.dentalcore.reporting.internal.dto.ReportDtos.ArAgingReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.ArAgingRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.CollectionsRow;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accounts-receivable read models. Ledger entries roll up to the account's
 * guarantor (patients.guarantor_id, self when null). Open balances are aged by
 * the charge's entry_date after applying all credits (payments, insurance
 * payments, and negative adjustments) to the account's charges oldest-first
 * (FIFO) — so a payment always retires the oldest debt before newer ones.
 */
@Service
@Transactional(readOnly = true)
public class ReceivablesReportService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicTimeService clinicTime;

    public ReceivablesReportService(NamedParameterJdbcTemplate jdbc,
                                    ClinicTimeService clinicTime) {
        this.jdbc = jdbc;
        this.clinicTime = clinicTime;
    }

    public ArAgingReport arAging() {
        List<AccountAging> accounts = agedAccounts();
        List<ArAgingRow> rows = accounts.stream()
                .map(a -> new ArAgingRow(
                        a.accountId.toString(), a.name, a.phone,
                        a.current, a.days30, a.days60, a.days90plus,
                        a.total(), a.lastPaymentDate))
                .sorted(Comparator.comparing(ArAgingRow::total).reversed())
                .toList();
        ArAgingBuckets buckets = new ArAgingBuckets(
                sum(rows, ArAgingRow::current),
                sum(rows, ArAgingRow::days30),
                sum(rows, ArAgingRow::days60),
                sum(rows, ArAgingRow::days90plus),
                sum(rows, ArAgingRow::total));
        return new ArAgingReport(buckets, rows);
    }

    public List<CollectionsRow> collections() {
        return agedAccounts().stream()
                .filter(a -> a.overdue().signum() > 0)
                .map(a -> new CollectionsRow(
                        a.accountId.toString(), a.name, a.phone,
                        a.overdue(), a.lastPaymentDate, a.oldestOpenChargeDate))
                .sorted(Comparator.comparing(CollectionsRow::totalOverdue).reversed())
                .toList();
    }

    // ---- shared aging model ----

    private static final class AccountAging {
        final UUID accountId;
        String name;
        String phone;
        final List<LocalDate> chargeDates = new ArrayList<>();
        final List<BigDecimal> chargeAmounts = new ArrayList<>();
        BigDecimal credits = BigDecimal.ZERO;
        LocalDate lastPaymentDate;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days30 = BigDecimal.ZERO;
        BigDecimal days60 = BigDecimal.ZERO;
        BigDecimal days90plus = BigDecimal.ZERO;
        LocalDate oldestOpenChargeDate;

        AccountAging(UUID accountId) {
            this.accountId = accountId;
        }

        BigDecimal total() {
            return current.add(days30).add(days60).add(days90plus);
        }

        BigDecimal overdue() {
            return days30.add(days60).add(days90plus);
        }
    }

    /**
     * Builds the per-account aging picture: positive entries are debt,
     * negative entries form one credit pool applied FIFO. Only accounts that
     * still owe something are returned.
     */
    private List<AccountAging> agedAccounts() {
        LocalDate today = clinicTime.today(DEFAULT_CLINIC_ID);
        Map<UUID, AccountAging> accounts = new LinkedHashMap<>();

        // entries arrive oldest-first so charge lists are already FIFO-ordered
        jdbc.query("""
                SELECT COALESCE(p.guarantor_id, p.id) AS account_id,
                       e.type, e.amount, e.entry_date
                FROM ledger_entries e
                JOIN patients p ON p.id = e.patient_id
                WHERE p.deleted_at IS NULL
                ORDER BY e.entry_date, e.created_at
                """, Map.of(), rs -> {
            UUID accountId = rs.getObject("account_id", UUID.class);
            AccountAging account =
                    accounts.computeIfAbsent(accountId, AccountAging::new);
            BigDecimal amount = rs.getBigDecimal("amount");
            LocalDate entryDate = rs.getObject("entry_date", LocalDate.class);
            String type = rs.getString("type");
            if (amount.signum() > 0) {
                account.chargeDates.add(entryDate);
                account.chargeAmounts.add(amount);
            } else {
                account.credits = account.credits.add(amount.negate());
            }
            if (("PAYMENT".equals(type) || "INSURANCE_PAYMENT".equals(type))
                    && (account.lastPaymentDate == null
                        || entryDate.isAfter(account.lastPaymentDate))) {
                account.lastPaymentDate = entryDate;
            }
        });

        List<AccountAging> owing = new ArrayList<>();
        for (AccountAging account : accounts.values()) {
            applyFifo(account, today);
            if (account.total().signum() > 0) {
                owing.add(account);
            }
        }
        if (owing.isEmpty()) {
            return owing;
        }
        decorate(owing);
        return owing;
    }

    private void applyFifo(AccountAging account, LocalDate today) {
        BigDecimal pool = account.credits;
        for (int i = 0; i < account.chargeAmounts.size(); i++) {
            BigDecimal open = account.chargeAmounts.get(i);
            if (pool.signum() > 0) {
                BigDecimal applied = pool.min(open);
                open = open.subtract(applied);
                pool = pool.subtract(applied);
            }
            if (open.signum() <= 0) {
                continue;
            }
            LocalDate chargeDate = account.chargeDates.get(i);
            if (account.oldestOpenChargeDate == null) {
                account.oldestOpenChargeDate = chargeDate;
            }
            long age = ChronoUnit.DAYS.between(chargeDate, today);
            if (age <= 30) {
                account.current = account.current.add(open);
            } else if (age <= 60) {
                account.days30 = account.days30.add(open);
            } else if (age <= 90) {
                account.days60 = account.days60.add(open);
            } else {
                account.days90plus = account.days90plus.add(open);
            }
        }
    }

    /** Looks up guarantor names and best phone for the accounts that owe. */
    private void decorate(List<AccountAging> owing) {
        Map<UUID, AccountAging> byId = new LinkedHashMap<>();
        owing.forEach(a -> byId.put(a.accountId, a));
        jdbc.query("""
                SELECT p.id,
                       p.last_name || ', ' || p.first_name AS guarantor_name,
                       ph.number AS phone
                FROM patients p
                LEFT JOIN LATERAL (
                    SELECT number FROM patient_phones
                    WHERE patient_id = p.id
                    ORDER BY is_primary DESC, created_at
                    LIMIT 1
                ) ph ON true
                WHERE p.id IN (:ids)
                """, Map.of("ids", byId.keySet()), rs -> {
            AccountAging account = byId.get(rs.getObject("id", UUID.class));
            if (account != null) {
                account.name = rs.getString("guarantor_name");
                account.phone = rs.getString("phone");
            }
        });
    }

    private static BigDecimal sum(List<ArAgingRow> rows,
                                  java.util.function.Function<ArAgingRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
