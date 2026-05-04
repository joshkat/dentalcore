package com.dentalcore.reporting.internal.web;

import com.dentalcore.reporting.internal.dto.ReportDtos.AsapRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.DailyProductionReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.DashboardSummary;
import com.dentalcore.reporting.internal.dto.ReportDtos.DaySheetReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.PatientGrowthRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderAppointmentsRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderUtilizationRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.UnscheduledTreatmentRow;
import com.dentalcore.reporting.internal.service.ReportingService;
import com.dentalcore.reporting.internal.service.WorkflowReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    /** Financial reports stay with billing-capable roles. */
    private static final String FINANCIAL = "hasAnyRole('ADMIN','BILLING')";

    /** Operational reports for staff roles; READ_ONLY only sees the dashboard. */
    private static final String OPERATIONAL =
            "hasAnyRole('ADMIN','DENTIST','HYGIENIST','FRONT_DESK','BILLING')";

    /** The day sheet is end-of-day reconciliation: billing roles plus the front desk. */
    private static final String DAY_SHEET = "hasAnyRole('ADMIN','BILLING','FRONT_DESK')";

    private final ReportingService service;
    private final WorkflowReportService workflowService;

    public ReportController(ReportingService service, WorkflowReportService workflowService) {
        this.service = service;
        this.workflowService = workflowService;
    }

    @GetMapping("/day-sheet")
    @PreAuthorize(DAY_SHEET)
    @Operation(summary = "Day sheet: production, collections, and deposit slip for one date")
    public DaySheetReport daySheet(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return workflowService.daySheet(date);
    }

    @GetMapping("/unscheduled-treatment")
    @PreAuthorize(OPERATIONAL)
    @Operation(summary = "Patients with approved planned work and no upcoming appointment")
    public List<UnscheduledTreatmentRow> unscheduledTreatment() {
        return workflowService.unscheduledTreatment();
    }

    @GetMapping("/asap-list")
    @PreAuthorize(OPERATIONAL)
    @Operation(summary = "Upcoming appointments flagged ASAP (short-notice fill-ins)")
    public List<AsapRow> asapList() {
        return workflowService.asapList();
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Today-at-a-glance counters for the dashboard")
    public DashboardSummary dashboard() {
        return service.dashboard();
    }

    @GetMapping("/appointments-by-provider")
    @PreAuthorize(OPERATIONAL)
    @Operation(summary = "Appointment counts per provider, broken out by status")
    public List<ProviderAppointmentsRow> appointmentsByProvider(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.appointmentsByProvider(from, to);
    }

    @GetMapping("/daily-production")
    @PreAuthorize(FINANCIAL)
    @Operation(summary = "Charges, collections, and adjustments per day (ADMIN/BILLING)")
    public DailyProductionReport dailyProduction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.dailyProduction(from, to);
    }

    @GetMapping("/patient-growth")
    @PreAuthorize(OPERATIONAL)
    @Operation(summary = "New patients per month with cumulative total")
    public List<PatientGrowthRow> patientGrowth(
            @RequestParam(defaultValue = "12") int months) {
        return service.patientGrowth(months);
    }

    @GetMapping("/provider-utilization")
    @PreAuthorize(OPERATIONAL)
    @Operation(summary = "Booked vs completed chair time per provider")
    public List<ProviderUtilizationRow> providerUtilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.providerUtilization(from, to);
    }
}
