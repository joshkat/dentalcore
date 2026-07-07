package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.dto.BillingDtos.FamilyLedgerEntryResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.FamilyLedgerResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.FamilyMemberBalance;
import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
import com.dentalcore.infrastructure.i18n.PdfMessages;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account-level (guarantor) views of the ledger: the guarantor's own entries
 * plus every patient whose account rolls up to them (patients.guarantor_id).
 */
@Service
@Transactional(readOnly = true)
public class FamilyBillingService {

    private static final int MAX_ENTRIES = 200;

    private final LedgerEntryRepository ledger;
    private final PatientApi patientApi;
    private final ProcedureCatalogApi catalogApi;
    private final PdfMessages messages;

    public FamilyBillingService(LedgerEntryRepository ledger,
                                PatientApi patientApi,
                                ProcedureCatalogApi catalogApi,
                                PdfMessages messages) {
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.catalogApi = catalogApi;
        this.messages = messages;
    }

    public FamilyLedgerResponse familyLedger(UUID guarantorId) {
        List<PatientSummary> family = familyOf(guarantorId);
        PatientSummary guarantor = family.get(0);
        Map<UUID, String> names = nameIndex(family);

        List<FamilyMemberBalance> members = family.stream()
                .map(member -> new FamilyMemberBalance(
                        member.id(), names.get(member.id()), ledger.balanceFor(member.id())))
                .toList();
        BigDecimal totalBalance = members.stream()
                .map(FamilyMemberBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LedgerEntry> entries = ledger
                .findByPatientIdInOrderByEntryDateDescCreatedAtDesc(
                        names.keySet(), PageRequest.of(0, MAX_ENTRIES))
                .getContent();
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                entries.stream()
                        .map(LedgerEntry::getProcedureCodeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));

        return new FamilyLedgerResponse(
                guarantor.id(),
                names.get(guarantor.id()),
                members,
                entries.stream().map(entry -> toEntryResponse(entry,
                        catalog.get(entry.getProcedureCodeId()),
                        ledger.existsByReversalOf(entry.getId()),
                        names.get(entry.getPatientId()))).toList(),
                totalBalance);
    }

    public byte[] familyStatementPdf(UUID guarantorId, LocalDate from, LocalDate to,
                                     String lang) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'to' must not precede 'from'");
        }
        List<PatientSummary> family = familyOf(guarantorId);
        Map<UUID, String> names = nameIndex(family);

        Map<UUID, List<LedgerEntry>> byPatient = ledger
                .findByPatientIdInAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
                        names.keySet(), from, to)
                .stream()
                .collect(Collectors.groupingBy(LedgerEntry::getPatientId));

        String html = buildHtml(family, names, byPatient, from, to, lang);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Family statement rendering failed", e);
        }
    }

    /** Guarantor first, then the patients who roll up to them. */
    private List<PatientSummary> familyOf(UUID guarantorId) {
        PatientSummary guarantor = patientApi.findSummary(guarantorId)
                .orElseThrow(() -> new InvalidRequestException("Unknown guarantor"));
        List<PatientSummary> family = new ArrayList<>();
        family.add(guarantor);
        family.addAll(patientApi.findDependents(guarantorId));
        return family;
    }

    private Map<UUID, String> nameIndex(List<PatientSummary> family) {
        Map<UUID, String> names = new LinkedHashMap<>();
        family.forEach(member ->
                names.put(member.id(), member.lastName() + ", " + member.firstName()));
        return names;
    }

    private FamilyLedgerEntryResponse toEntryResponse(LedgerEntry entry,
                                                      ProcedureSummary procedure,
                                                      boolean reversed,
                                                      String patientName) {
        return new FamilyLedgerEntryResponse(
                entry.getId(), entry.getType().name(), entry.getAmount(),
                entry.getDescription(),
                entry.getMethod() == null ? null : entry.getMethod().name(),
                entry.getProcedureCodeId(),
                procedure != null ? procedure.code() : null,
                entry.getAppointmentId(), entry.getClaimId(), entry.getEntryDate(),
                entry.getReversalOf(), reversed, entry.getCreatedAt(),
                entry.getPatientId(), patientName);
    }

    private String buildHtml(List<PatientSummary> family, Map<UUID, String> names,
                             Map<UUID, List<LedgerEntry>> byPatient,
                             LocalDate from, LocalDate to, String lang) {
        DateTimeFormatter dateFormat = messages.dateFormatter(lang);
        StringBuilder sections = new StringBuilder();
        BigDecimal familyTotal = BigDecimal.ZERO;

        for (PatientSummary member : family) {
            List<LedgerEntry> entries =
                    byPatient.getOrDefault(member.id(), List.of());
            BigDecimal subtotal = entries.stream()
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            familyTotal = familyTotal.add(subtotal);

            StringBuilder rows = new StringBuilder();
            for (LedgerEntry entry : entries) {
                rows.append("""
                        <tr>
                          <td>%s</td><td>%s</td><td>%s</td><td class="num">%s</td>
                        </tr>
                        """.formatted(
                        entry.getEntryDate().format(dateFormat),
                        entry.getType().name().replace('_', ' '),
                        HtmlUtils.htmlEscape(entry.getDescription(), "UTF-8"),
                        money(entry.getAmount())));
            }
            if (entries.isEmpty()) {
                rows.append("<tr><td colspan=\"4\">%s</td></tr>"
                        .formatted(msg(lang, "statement.noActivity")));
            }

            sections.append("""
                    <h2>%s</h2>
                    <table>
                      <thead><tr><th>%s</th><th>%s</th><th>%s</th>
                      <th class="num">%s</th></tr></thead>
                      <tbody>%s</tbody>
                      <tfoot><tr><td colspan="3" class="subtotal">%s</td>
                      <td class="num subtotal">%s</td></tr></tfoot>
                    </table>
                    """.formatted(
                    HtmlUtils.htmlEscape(names.get(member.id()), "UTF-8"),
                    msg(lang, "common.date"),
                    msg(lang, "common.type"),
                    msg(lang, "common.description"),
                    msg(lang, "common.amount"),
                    rows,
                    msg(lang, "family.subtotal"),
                    money(subtotal)));
        }

        return """
                <!DOCTYPE html>
                <html><head><style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #111; }
                  h1 { font-size: 18px; margin-bottom: 0; }
                  h2 { font-size: 13px; margin: 16px 0 0 0; }
                  .muted { color: #666; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 6px; }
                  th, td { text-align: left; padding: 5px 6px; border-bottom: 1px solid #ddd; }
                  th { background: #f3f4f6; font-size: 10px; text-transform: uppercase; }
                  .num { text-align: right; }
                  .subtotal { font-weight: bold; }
                  .totals { margin-top: 14px; width: 40%%; margin-left: 60%%; }
                  .totals td { border: none; padding: 3px 6px; }
                  .balance { font-size: 14px; font-weight: bold; }
                </style></head>
                <body>
                  <h1>%s</h1>
                  <p class="muted">%s</p>
                  <p><strong>%s</strong></p>
                  <p class="muted">%s</p>
                  %s
                  <table class="totals">
                    <tr class="balance"><td>%s</td><td class="num">%s</td></tr>
                  </table>
                  <p class="muted">%s</p>
                </body></html>
                """.formatted(
                msg(lang, "family.title"),
                msg(lang, "common.clinic"),
                msg(lang, "family.accountOf",
                        names.get(family.get(0).id())),
                msg(lang, "statement.period",
                        from.format(dateFormat), to.format(dateFormat),
                        LocalDate.now().format(dateFormat)),
                sections,
                msg(lang, "family.total"),
                money(familyTotal),
                msg(lang, "common.balanceFooter"));
    }

    private String msg(String lang, String key, Object... args) {
        String value = messages.get(lang, key, args);
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private String money(BigDecimal amount) {
        return (amount.signum() < 0 ? "-RD$" : "RD$") + amount.abs();
    }
}
