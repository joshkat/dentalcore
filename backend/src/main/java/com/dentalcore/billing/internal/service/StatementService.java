package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
import com.dentalcore.infrastructure.i18n.PdfMessages;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
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
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StatementService {

    private final LedgerEntryRepository ledger;
    private final PatientApi patientApi;
    private final PdfMessages messages;

    public StatementService(LedgerEntryRepository ledger, PatientApi patientApi,
                            PdfMessages messages) {
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.messages = messages;
    }

    public byte[] statementPdf(UUID patientId, LocalDate from, LocalDate to, String lang) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'to' must not precede 'from'");
        }
        PatientSummary patient = patientApi.findSummary(patientId)
                .orElseThrow(() -> new InvalidRequestException("Unknown patient"));

        List<LedgerEntry> entries = ledger
                .findByPatientIdOrderByEntryDateDescCreatedAtDesc(
                        patientId, PageRequest.of(0, 1000))
                .getContent().stream()
                .filter(e -> !e.getEntryDate().isBefore(from) && !e.getEntryDate().isAfter(to))
                .sorted((a, b) -> a.getEntryDate().compareTo(b.getEntryDate()))
                .toList();
        BigDecimal periodTotal = entries.stream()
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = ledger.balanceFor(patientId);

        String html = buildHtml(patient, from, to, entries, periodTotal, balance, lang);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Statement rendering failed", e);
        }
    }

    private String buildHtml(PatientSummary patient, LocalDate from, LocalDate to,
                             List<LedgerEntry> entries, BigDecimal periodTotal,
                             BigDecimal balance, String lang) {
        DateTimeFormatter dateFormat = messages.dateFormatter(lang);
        StringBuilder rows = new StringBuilder();
        for (LedgerEntry entry : entries) {
            rows.append("""
                    <tr>
                      <td>%s</td><td>%s</td><td>%s</td><td class="num">%s</td>
                    </tr>
                    """.formatted(
                    entry.getEntryDate().format(dateFormat),
                    entry.getType().name().replace('_', ' '),
                    escape(entry.getDescription()),
                    money(entry.getAmount())));
        }
        if (entries.isEmpty()) {
            rows.append("<tr><td colspan=\"4\">%s</td></tr>"
                    .formatted(msg(lang, "statement.noActivity")));
        }

        return """
                <!DOCTYPE html>
                <html><head><style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #111; }
                  h1 { font-size: 18px; margin-bottom: 0; }
                  .muted { color: #666; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 12px; }
                  th, td { text-align: left; padding: 5px 6px; border-bottom: 1px solid #ddd; }
                  th { background: #f3f4f6; font-size: 10px; text-transform: uppercase; }
                  .num { text-align: right; }
                  .totals { margin-top: 14px; width: 40%%; margin-left: 60%%; }
                  .totals td { border: none; padding: 3px 6px; }
                  .balance { font-size: 14px; font-weight: bold; }
                </style></head>
                <body>
                  <h1>%s</h1>
                  <p class="muted">%s</p>
                  <p><strong>%s, %s</strong><br/>%s %s</p>
                  <p class="muted">%s</p>
                  <table>
                    <thead><tr><th>%s</th><th>%s</th><th>%s</th>
                    <th class="num">%s</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr><td>%s</td><td class="num">%s</td></tr>
                    <tr class="balance"><td>%s</td><td class="num">%s</td></tr>
                  </table>
                  <p class="muted">%s</p>
                </body></html>
                """.formatted(
                msg(lang, "statement.title"),
                msg(lang, "common.clinic"),
                escape(patient.lastName()),
                escape(patient.firstName()),
                msg(lang, "common.dob"),
                patient.dateOfBirth(),
                msg(lang, "statement.period",
                        from.format(dateFormat), to.format(dateFormat),
                        LocalDate.now().format(dateFormat)),
                msg(lang, "common.date"),
                msg(lang, "common.type"),
                msg(lang, "common.description"),
                msg(lang, "common.amount"),
                rows,
                msg(lang, "statement.periodActivity"),
                money(periodTotal),
                msg(lang, "common.currentBalance"),
                money(balance),
                msg(lang, "common.balanceFooter"));
    }

    private String msg(String lang, String key, Object... args) {
        return escape(messages.get(lang, key, args));
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private String money(BigDecimal amount) {
        return (amount.signum() < 0 ? "-RD$" : "RD$") + amount.abs();
    }
}
