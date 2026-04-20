package com.dentalcore.reporting.internal.web;

import com.dentalcore.reporting.internal.dto.ReportDtos.DailyProductionReport;
import com.dentalcore.reporting.internal.dto.ReportDtos.DashboardSummary;
import com.dentalcore.reporting.internal.dto.ReportDtos.PatientGrowthRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderAppointmentsRow;
import com.dentalcore.reporting.internal.dto.ReportDtos.ProviderUtilizationRow;
import com.dentalcore.reporting.internal.service.ReportingService;
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

    private final ReportingService service;

    public ReportController(ReportingService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Today-at-a-glance counters for the dashboard")
    public DashboardSummary dashboard() {
        return service.dashboard();
    }

    @GetMapping("/appointments-by-provider")
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
    @Operation(summary = "New patients per month with cumulative total")
    public List<PatientGrowthRow> patientGrowth(
            @RequestParam(defaultValue = "12") int months) {
        return service.patientGrowth(months);
    }

    @GetMapping("/provider-utilization")
    @Operation(summary = "Booked vs completed chair time per provider")
    public List<ProviderUtilizationRow> providerUtilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.providerUtilization(from, to);
    }
}
