package com.dentalcore.billing.internal.service;

import com.dentalcore.appointments.api.AppointmentApi;
import com.dentalcore.appointments.api.AppointmentView;
import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
import com.dentalcore.infrastructure.i18n.PdfMessages;
import com.dentalcore.infrastructure.time.ClinicTimeService;
import com.dentalcore.insurance.api.InsuranceEstimateApi;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.procedures.api.CompletedProcedureApi;
import com.dentalcore.procedures.api.CompletedProcedureView;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Walk-out statement: what was done today, what was paid today, what insurance
 * is expected to cover, and where the balance stands — handed to the patient
 * at checkout. Strict XHTML for openhtmltopdf: numeric character references
 * only, all user data escaped.
 */
@Service
@Transactional(readOnly = true)
public class WalkoutService {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AppointmentApi appointmentApi;
    private final CompletedProcedureApi completedProcedureApi;
    private final LedgerEntryRepository ledger;
    private final PatientApi patientApi;
    private final InsuranceEstimateApi estimateApi;
    private final ClinicTimeService clinicTime;
    private final PdfMessages messages;

    public WalkoutService(AppointmentApi appointmentApi,
                          CompletedProcedureApi completedProcedureApi,
                          LedgerEntryRepository ledger,
                          PatientApi patientApi,
                          InsuranceEstimateApi estimateApi,
                          ClinicTimeService clinicTime,
                          PdfMessages messages) {
        this.appointmentApi = appointmentApi;
        this.completedProcedureApi = completedProcedureApi;
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.estimateApi = estimateApi;
        this.clinicTime = clinicTime;
        this.messages = messages;
    }

    public byte[] walkoutPdf(UUID appointmentId, String lang) {
        AppointmentView appointment = appointmentApi.findView(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        PatientSummary patient = patientApi.findSummary(appointment.patientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient", appointment.patientId()));

        List<CompletedProcedureView> procedures =
                completedProcedureApi.findForAppointment(appointmentId);
        LocalDate date = procedures.stream()
                .map(CompletedProcedureView::entryDate)
                .max(LocalDate::compareTo)
                .orElseGet(() -> clinicTime.today(DEFAULT_CLINIC_ID));

        List<LedgerEntry> payments = ledger.findByPatientIdAndEntryDateAndTypeOrderByCreatedAtAsc(
                appointment.patientId(), date, LedgerEntry.Type.PAYMENT);

        InsuranceEstimateApi.EstimateResult estimate = procedures.isEmpty()
                ? null
                : estimateApi.estimateFor(appointment.patientId(), procedures.stream()
                .map(p -> new InsuranceEstimateApi.EstimateItem(p.procedureCodeId(), p.fee()))
                .toList());

        BigDecimal balance = ledger.balanceFor(appointment.patientId());

        String html = buildHtml(patient, date, procedures, payments, estimate, balance, lang);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Walk-out rendering failed", e);
        }
    }

    private String buildHtml(PatientSummary patient, LocalDate date,
                             List<CompletedProcedureView> procedures,
                             List<LedgerEntry> payments,
                             InsuranceEstimateApi.EstimateResult estimate,
                             BigDecimal balance, String lang) {
        DateTimeFormatter dateFormat = messages.dateFormatter(lang);

        StringBuilder procedureRows = new StringBuilder();
        BigDecimal procedureTotal = BigDecimal.ZERO;
        for (CompletedProcedureView procedure : procedures) {
            procedureTotal = procedureTotal.add(procedure.fee());
            procedureRows.append("""
                    <tr>
                      <td>%s</td><td>%s</td><td>%s</td><td class="num">%s</td>
                    </tr>
                    """.formatted(
                    escape(procedure.code()),
                    escape(procedure.description()),
                    procedure.tooth() == null ? "" : escape(procedure.tooth()),
                    money(procedure.fee())));
        }
        if (procedures.isEmpty()) {
            procedureRows.append("<tr><td colspan=\"4\">%s</td></tr>"
                    .formatted(msg(lang, "walkout.noProcedures")));
        }

        StringBuilder paymentRows = new StringBuilder();
        BigDecimal paymentTotal = BigDecimal.ZERO;
        for (LedgerEntry payment : payments) {
            paymentTotal = paymentTotal.add(payment.getAmount().abs());
            paymentRows.append("""
                    <tr>
                      <td>%s</td><td class="num">%s</td>
                    </tr>
                    """.formatted(
                    payment.getMethod() == null ? "OTHER" : payment.getMethod().name(),
                    money(payment.getAmount().abs())));
        }
        if (payments.isEmpty()) {
            paymentRows.append("<tr><td colspan=\"2\">%s</td></tr>"
                    .formatted(msg(lang, "walkout.noPayments")));
        }

        String insuranceBlock = "";
        if (estimate != null && estimate.hasCoverage()) {
            insuranceBlock = """
                    <h2>%s</h2>
                    <p class="muted">%s &#183; %s</p>
                    <table class="totals-left">
                      <tr><td>%s</td><td class="num">%s</td></tr>
                      <tr><td>%s</td><td class="num">%s</td></tr>
                    </table>
                    <p class="muted">%s</p>
                    """.formatted(
                    msg(lang, "walkout.insuranceEstimate"),
                    escape(estimate.carrierName()),
                    escape(estimate.planName()),
                    msg(lang, "walkout.insurancePortion"),
                    money(estimate.totalInsurance()),
                    msg(lang, "walkout.patientPortion"),
                    money(estimate.totalPatient()),
                    msg(lang, "walkout.estimateDisclaimer"));
        }

        return """
                <!DOCTYPE html>
                <html><head><style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #111; }
                  h1 { font-size: 18px; margin-bottom: 0; }
                  h2 { font-size: 13px; margin: 14px 0 2px 0; }
                  .muted { color: #666; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 6px; }
                  th, td { text-align: left; padding: 5px 6px; border-bottom: 1px solid #ddd; }
                  th { background: #f3f4f6; font-size: 10px; text-transform: uppercase; }
                  .num { text-align: right; }
                  .totals { margin-top: 14px; width: 40%%; margin-left: 60%%; }
                  .totals td { border: none; padding: 3px 6px; }
                  .totals-left { width: 60%%; }
                  .totals-left td { border: none; padding: 3px 6px; }
                  .balance { font-size: 14px; font-weight: bold; }
                </style></head>
                <body>
                  <h1>%s</h1>
                  <p class="muted">%s</p>
                  <p><strong>%s, %s</strong><br/>%s %s</p>
                  <p class="muted">%s</p>

                  <h2>%s</h2>
                  <table>
                    <thead><tr><th>%s</th><th>%s</th><th>%s</th>
                    <th class="num">%s</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr class="balance"><td>%s</td><td class="num">%s</td></tr>
                  </table>

                  <h2>%s</h2>
                  <table>
                    <thead><tr><th>%s</th><th class="num">%s</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr><td>%s</td><td class="num">%s</td></tr>
                  </table>

                  %s

                  <table class="totals">
                    <tr class="balance"><td>%s</td><td class="num">%s</td></tr>
                  </table>
                  <p class="muted">%s</p>
                </body></html>
                """.formatted(
                msg(lang, "walkout.title"),
                msg(lang, "common.clinic"),
                escape(patient.lastName()),
                escape(patient.firstName()),
                msg(lang, "common.dob"),
                patient.dateOfBirth(),
                msg(lang, "walkout.visitLine",
                        date.format(dateFormat), LocalDate.now().format(dateFormat)),
                msg(lang, "walkout.completedProcedures"),
                msg(lang, "common.code"),
                msg(lang, "common.description"),
                msg(lang, "common.tooth"),
                msg(lang, "common.fee"),
                procedureRows,
                msg(lang, "walkout.totalCharges"),
                money(procedureTotal),
                msg(lang, "walkout.paymentsToday"),
                msg(lang, "common.method"),
                msg(lang, "common.amount"),
                paymentRows,
                msg(lang, "walkout.totalPaidToday"),
                money(paymentTotal),
                insuranceBlock,
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
        return (amount.signum() < 0 ? "-$" : "$") + amount.abs();
    }
}
