package com.dentalcore.billing.internal.service;

import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
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

    public StatementService(LedgerEntryRepository ledger, PatientApi patientApi) {
        this.ledger = ledger;
        this.patientApi = patientApi;
    }

    public byte[] statementPdf(UUID patientId, LocalDate from, LocalDate to) {
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

        String html = buildHtml(patient, from, to, entries, periodTotal, balance);
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
                             BigDecimal balance) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy");
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
            rows.append("<tr><td colspan=\"4\">No activity in this period.</td></tr>");
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
                  <h1>DentalCore — Patient Statement</h1>
                  <p class="muted">Main Clinic</p>
                  <p><strong>%s, %s</strong><br/>DOB %s</p>
                  <p class="muted">Statement period: %s — %s &#183; Generated %s</p>
                  <table>
                    <thead><tr><th>Date</th><th>Type</th><th>Description</th>
                    <th class="num">Amount</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr><td>Period activity</td><td class="num">%s</td></tr>
                    <tr class="balance"><td>Current balance</td><td class="num">%s</td></tr>
                  </table>
                  <p class="muted">A positive balance is the amount due. Please contact the
                  office with any questions.</p>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(patient.lastName(), "UTF-8"),
                HtmlUtils.htmlEscape(patient.firstName(), "UTF-8"),
                patient.dateOfBirth(),
                from.format(dateFormat), to.format(dateFormat),
                LocalDate.now().format(dateFormat),
                rows,
                money(periodTotal),
                money(balance));
    }

    private String money(BigDecimal amount) {
        return (amount.signum() < 0 ? "-$" : "$") + amount.abs();
    }
}
