package com.dentalcore.forms.internal.service;

import com.dentalcore.infrastructure.i18n.PdfMessages;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Renders a signed form as a PDF: template name, patient, ordered Q/A pairs,
 * the drawn signature image and the signing attestation. Strict XHTML with
 * numeric character references only (openhtmltopdf parses XML, not HTML5).
 * Fixed labels come from the localized PDF message bundles.
 */
@Component
public class FormPdfRenderer {

    private final PdfMessages messages;

    public FormPdfRenderer(PdfMessages messages) {
        this.messages = messages;
    }

    public byte[] render(String templateName, String patientName,
                         List<Map<String, Object>> fields, Map<String, Object> answers,
                         byte[] signaturePng, String signedByName, Instant signedAt,
                         String lang) {
        String html = buildHtml(templateName, patientName, fields, answers,
                signaturePng, signedByName, signedAt, lang);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Form rendering failed", e);
        }
    }

    private String buildHtml(String templateName, String patientName,
                             List<Map<String, Object>> fields, Map<String, Object> answers,
                             byte[] signaturePng, String signedByName, Instant signedAt,
                             String lang) {
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> field : fields) {
            String key = String.valueOf(field.get("key"));
            String label = String.valueOf(field.get("label"));
            rows.append("""
                    <tr>
                      <td class="q">%s</td><td>%s</td>
                    </tr>
                    """.formatted(
                    HtmlUtils.htmlEscape(label, "UTF-8"),
                    HtmlUtils.htmlEscape(answerText(field, answers.get(key), lang), "UTF-8")));
        }

        String signedAtText = messages.dateTime(lang, signedAt,
                ZoneId.of("America/New_York"));

        return """
                <!DOCTYPE html>
                <html><head><style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #111; }
                  h1 { font-size: 18px; margin-bottom: 0; }
                  .muted { color: #666; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 12px; }
                  td { text-align: left; padding: 5px 6px; border-bottom: 1px solid #ddd;
                       vertical-align: top; }
                  .q { width: 40%%; font-weight: bold; }
                  .signature { margin-top: 24px; }
                  .signature img { max-height: 90px; }
                </style></head>
                <body>
                  <h1>%s</h1>
                  <p class="muted">%s</p>
                  <p><strong>%s: %s</strong></p>
                  <table><tbody>%s</tbody></table>
                  <div class="signature">
                    <p class="muted">%s</p>
                    <img src="data:image/png;base64,%s" alt="signature"/>
                    <p>%s <strong>%s</strong> %s %s</p>
                  </div>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(templateName, "UTF-8"),
                msg(lang, "form.clinicLine"),
                msg(lang, "form.patient"),
                HtmlUtils.htmlEscape(patientName, "UTF-8"),
                rows,
                msg(lang, "form.signatureLabel"),
                Base64.getEncoder().encodeToString(signaturePng),
                msg(lang, "form.signedBy"),
                HtmlUtils.htmlEscape(signedByName, "UTF-8"),
                msg(lang, "form.signedOn"),
                HtmlUtils.htmlEscape(signedAtText, "UTF-8"));
    }

    private String answerText(Map<String, Object> field, Object answer, String lang) {
        if (answer == null || String.valueOf(answer).isBlank()) {
            return "—"; // em dash for unanswered optional fields
        }
        if ("CHECKBOX".equals(field.get("type"))) {
            return Boolean.parseBoolean(String.valueOf(answer))
                    ? messages.get(lang, "common.yes")
                    : messages.get(lang, "common.no");
        }
        return String.valueOf(answer);
    }

    private String msg(String lang, String key) {
        return HtmlUtils.htmlEscape(messages.get(lang, key), "UTF-8");
    }
}
