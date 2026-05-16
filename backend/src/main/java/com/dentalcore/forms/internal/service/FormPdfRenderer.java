package com.dentalcore.forms.internal.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Renders a signed form as a PDF: template name, patient, ordered Q/A pairs,
 * the drawn signature image and the signing attestation. Strict XHTML with
 * numeric character references only (openhtmltopdf parses XML, not HTML5).
 */
@Component
public class FormPdfRenderer {

    public byte[] render(String templateName, String patientName,
                         List<Map<String, Object>> fields, Map<String, Object> answers,
                         byte[] signaturePng, String signedByName, Instant signedAt) {
        String html = buildHtml(templateName, patientName, fields, answers,
                signaturePng, signedByName, signedAt);
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
                             byte[] signaturePng, String signedByName, Instant signedAt) {
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
                    HtmlUtils.htmlEscape(answerText(field, answers.get(key)), "UTF-8")));
        }

        String signedAtText = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm zzz")
                .withZone(ZoneId.of("America/New_York"))
                .format(signedAt);

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
                  <p class="muted">DentalCore &#8212; Main Clinic</p>
                  <p><strong>Patient: %s</strong></p>
                  <table><tbody>%s</tbody></table>
                  <div class="signature">
                    <p class="muted">Signature:</p>
                    <img src="data:image/png;base64,%s" alt="signature"/>
                    <p>Signed by <strong>%s</strong> on %s</p>
                  </div>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(templateName, "UTF-8"),
                HtmlUtils.htmlEscape(patientName, "UTF-8"),
                rows,
                Base64.getEncoder().encodeToString(signaturePng),
                HtmlUtils.htmlEscape(signedByName, "UTF-8"),
                HtmlUtils.htmlEscape(signedAtText, "UTF-8"));
    }

    private String answerText(Map<String, Object> field, Object answer) {
        if (answer == null || String.valueOf(answer).isBlank()) {
            return "—"; // em dash for unanswered optional fields
        }
        if ("CHECKBOX".equals(field.get("type"))) {
            return Boolean.parseBoolean(String.valueOf(answer)) ? "Yes" : "No";
        }
        return String.valueOf(answer);
    }
}
