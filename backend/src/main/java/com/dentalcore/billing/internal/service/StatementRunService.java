package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunDetailResponse;
import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunItemResponse;
import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunRequest;
import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunSummaryResponse;
import com.dentalcore.billing.internal.entity.StatementRun;
import com.dentalcore.billing.internal.entity.StatementRunItem;
import com.dentalcore.billing.internal.repository.StatementRunRepository;
import com.dentalcore.documents.api.DocumentIngestApi;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batch statement runs: render the family statement for every guarantor
 * account whose current family balance clears the run's minimum, file each
 * PDF in the guarantor's Documents, and keep a per-account record.
 *
 * <p>Accounts follow the receivables roll-up: a patient's ledger belongs to
 * COALESCE(guarantor_id, id), so the eligible set is every account with a
 * positive current balance — guarantors with dependents and self-guaranteed
 * patients with activity alike.
 */
@Service
@Transactional
public class StatementRunService {

    private static final Logger log = LoggerFactory.getLogger(StatementRunService.class);

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String STATEMENT_CATEGORY = "STATEMENT";
    private static final int MAX_RUNS_LISTED = 50;

    private final StatementRunRepository runs;
    private final FamilyBillingService familyBilling;
    private final DocumentIngestApi documentIngest;
    private final PatientApi patientApi;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public StatementRunService(StatementRunRepository runs,
                               FamilyBillingService familyBilling,
                               DocumentIngestApi documentIngest,
                               PatientApi patientApi,
                               NamedParameterJdbcTemplate jdbc,
                               ApplicationEventPublisher events) {
        this.runs = runs;
        this.familyBilling = familyBilling;
        this.documentIngest = documentIngest;
        this.patientApi = patientApi;
        this.jdbc = jdbc;
        this.events = events;
    }

    public StatementRunDetailResponse create(StatementRunRequest request) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new InvalidRequestException("'fromDate' must not be after 'toDate'");
        }
        BigDecimal minBalance = request.minBalance() == null
                ? BigDecimal.ZERO : request.minBalance();
        if (minBalance.signum() < 0) {
            throw new InvalidRequestException("'minBalance' must not be negative");
        }
        UUID userId = CurrentUser.id().orElse(null);

        StatementRun run = new StatementRun(DEFAULT_CLINIC_ID,
                request.fromDate(), request.toDate(), minBalance, userId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        StatementRun.Status status = StatementRun.Status.COMPLETED;
        try {
            for (AccountBalance account : eligibleAccounts(minBalance)) {
                byte[] pdf = familyBilling.familyStatementPdf(
                        account.guarantorPatientId(), request.fromDate(), request.toDate());
                UUID documentId = documentIngest.storePdf(
                        account.guarantorPatientId(), "Statement " + request.toDate(),
                        STATEMENT_CATEGORY, pdf, userId);
                run.addItem(account.guarantorPatientId(), account.balance(), documentId);
                totalAmount = totalAmount.add(account.balance());
            }
        } catch (RuntimeException e) {
            log.error("Statement run failed after {} statement(s)", run.getItems().size(), e);
            status = StatementRun.Status.FAILED;
        }
        run.finish(status, totalAmount);
        // flush so the generated createdAt timestamp is populated for the response
        run = runs.saveAndFlush(run);

        events.publishEvent(new AuditEvent(
                userId, "StatementRun", run.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("fromDate", run.getFromDate().toString(),
                        "toDate", run.getToDate().toString(),
                        "minBalance", run.getMinBalance().toString(),
                        "status", run.getStatus().name(),
                        "totalAccounts", String.valueOf(run.getTotalAccounts()),
                        "totalAmount", run.getTotalAmount().toString())));
        return toDetail(run);
    }

    @Transactional(readOnly = true)
    public List<StatementRunSummaryResponse> list() {
        return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_RUNS_LISTED))
                .getContent().stream()
                .map(run -> new StatementRunSummaryResponse(
                        run.getId(), run.getFromDate(), run.getToDate(),
                        run.getMinBalance(), run.getStatus().name(),
                        run.getTotalAccounts(), run.getTotalAmount(),
                        run.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StatementRunDetailResponse get(UUID id) {
        StatementRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement run", id));
        return toDetail(run);
    }

    /** Guarantor accounts whose current family balance is positive and clears the minimum. */
    private List<AccountBalance> eligibleAccounts(BigDecimal minBalance) {
        return jdbc.query("""
                SELECT COALESCE(p.guarantor_id, p.id) AS account_id,
                       SUM(e.amount) AS balance
                FROM ledger_entries e
                JOIN patients p ON p.id = e.patient_id
                WHERE p.deleted_at IS NULL
                GROUP BY COALESCE(p.guarantor_id, p.id)
                HAVING SUM(e.amount) > 0 AND SUM(e.amount) >= :minBalance
                ORDER BY balance DESC
                """,
                Map.of("minBalance", minBalance),
                (rs, rowNum) -> new AccountBalance(
                        rs.getObject("account_id", UUID.class),
                        rs.getBigDecimal("balance")));
    }

    private StatementRunDetailResponse toDetail(StatementRun run) {
        Map<UUID, PatientSummary> guarantors = patientApi.findSummaries(
                run.getItems().stream()
                        .map(StatementRunItem::getGuarantorPatientId)
                        .collect(Collectors.toSet()));
        return new StatementRunDetailResponse(
                run.getId(), run.getFromDate(), run.getToDate(), run.getMinBalance(),
                run.getStatus().name(), run.getTotalAccounts(), run.getTotalAmount(),
                run.getCreatedAt(),
                run.getItems().stream()
                        .map(item -> new StatementRunItemResponse(
                                item.getGuarantorPatientId(),
                                nameOf(guarantors.get(item.getGuarantorPatientId())),
                                item.getBalance(),
                                item.getDocumentId()))
                        .toList());
    }

    private String nameOf(PatientSummary patient) {
        return patient == null ? null : patient.lastName() + ", " + patient.firstName();
    }

    private record AccountBalance(UUID guarantorPatientId, BigDecimal balance) {
    }
}
