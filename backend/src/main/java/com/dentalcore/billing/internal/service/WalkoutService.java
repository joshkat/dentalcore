package com.dentalcore.billing.internal.service;

import com.dentalcore.appointments.api.AppointmentApi;
import com.dentalcore.appointments.api.AppointmentView;
import com.dentalcore.billing.internal.entity.LedgerEntry;
import com.dentalcore.billing.internal.repository.LedgerEntryRepository;
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

    public WalkoutService(AppointmentApi appointmentApi,
                          CompletedProcedureApi completedProcedureApi,
                          LedgerEntryRepository ledger,
                          PatientApi patientApi,
                          InsuranceEstimateApi estimateApi,
                          ClinicTimeService clinicTime) {
        this.appointmentApi = appointmentApi;
        this.completedProcedureApi = completedProcedureApi;
        this.ledger = ledger;
        this.patientApi = patientApi;
        this.estimateApi = estimateApi;
        this.clinicTime = clinicTime;
    }

    public byte[] walkoutPdf(UUID appointmentId) {
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

        String html = buildHtml(patient, date, procedures, payments, estimate, balance);
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
                             BigDecimal balance) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy");

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
            procedureRows.append(
                    "<tr><td colspan=\"4\">No completed procedures for this visit.</td></tr>");
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
            paymentRows.append("<tr><td colspan=\"2\">No payments posted today.</td></tr>");
        }

        String insuranceBlock = "";
        if (estimate != null && estimate.hasCoverage()) {
            insuranceBlock = """
                    <h2>Insurance estimate</h2>
                    <p class="muted">%s &#183; %s</p>
                    <table class="totals-left">
                      <tr><td>Estimated insurance portion</td><td class="num">%s</td></tr>
                      <tr><td>Estimated patient portion</td><td class="num">%s</td></tr>
                    </table>
                    <p class="muted">Estimates are not a guarantee of payment.</p>
                    """.formatted(
                    escape(estimate.carrierName()),
                    escape(estimate.planName()),
                    money(estimate.totalInsurance()),
                    money(estimate.totalPatient()));
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
                  <h1>DentalCore - Walk-Out Statement</h1>
                  <p class="muted">Main Clinic</p>
                  <p><strong>%s, %s</strong><br/>DOB %s</p>
                  <p class="muted">Visit date: %s &#183; Generated %s</p>

                  <h2>Completed procedures</h2>
                  <table>
                    <thead><tr><th>Code</th><th>Description</th><th>Tooth</th>
                    <th class="num">Fee</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr class="balance"><td>Total charges</td><td class="num">%s</td></tr>
                  </table>

                  <h2>Payments today</h2>
                  <table>
                    <thead><tr><th>Method</th><th class="num">Amount</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <table class="totals">
                    <tr><td>Total paid today</td><td class="num">%s</td></tr>
                  </table>

                  %s

                  <table class="totals">
                    <tr class="balance"><td>Current balance</td><td class="num">%s</td></tr>
                  </table>
                  <p class="muted">A positive balance is the amount due. Please contact the
                  office with any questions.</p>
                </body></html>
                """.formatted(
                escape(patient.lastName()),
                escape(patient.firstName()),
                patient.dateOfBirth(),
                date.format(dateFormat),
                LocalDate.now().format(dateFormat),
                procedureRows,
                money(procedureTotal),
                paymentRows,
                money(paymentTotal),
                insuranceBlock,
                money(balance));
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private String money(BigDecimal amount) {
        return (amount.signum() < 0 ? "-$" : "$") + amount.abs();
    }
}
