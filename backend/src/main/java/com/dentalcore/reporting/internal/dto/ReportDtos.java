package com.dentalcore.reporting.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ProviderAppointmentsRow(
            String providerId,
            String providerName,
            long scheduled,
            long confirmed,
            long checkedIn,
            long inProgress,
            long completed,
            long noShow,
            long cancelled,
            long total
    ) {
    }

    public record DailyProductionRow(
            LocalDate date,
            BigDecimal charges,
            BigDecimal patientPayments,
            BigDecimal insurancePayments,
            BigDecimal adjustments,
            BigDecimal net
    ) {
    }

    public record DailyProductionReport(
            List<DailyProductionRow> days,
            BigDecimal totalCharges,
            BigDecimal totalPatientPayments,
            BigDecimal totalInsurancePayments,
            BigDecimal totalAdjustments,
            BigDecimal totalNet
    ) {
    }

    public record PatientGrowthRow(
            String month,
            long newPatients,
            long cumulative
    ) {
    }

    public record ProviderUtilizationRow(
            String providerId,
            String providerName,
            long appointments,
            long bookedMinutes,
            long completedMinutes,
            long distinctPatients
    ) {
    }

    public record DashboardSummary(
            long activePatients,
            long todaysAppointments,
            long todaysCompletedAppointments,
            BigDecimal todaysProduction,
            BigDecimal todaysCollections,
            long openClaims
    ) {
    }
}
