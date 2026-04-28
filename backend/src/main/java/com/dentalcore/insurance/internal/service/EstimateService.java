package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.api.InsuranceEstimateApi;
import com.dentalcore.insurance.internal.entity.CoverageRule;
import com.dentalcore.insurance.internal.entity.FeeSchedule;
import com.dentalcore.insurance.internal.entity.FeeScheduleFee;
import com.dentalcore.insurance.internal.entity.InsurancePlan;
import com.dentalcore.insurance.internal.entity.PatientInsurance;
import com.dentalcore.insurance.internal.repository.ClaimRepository;
import com.dentalcore.insurance.internal.repository.CoverageRuleRepository;
import com.dentalcore.insurance.internal.repository.FeeScheduleRepository;
import com.dentalcore.insurance.internal.repository.PatientInsuranceRepository;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Insurance estimate engine. Model (documented simplifications):
 * - allowed fee = plan's fee-schedule fee, falling back to the gross fee
 * - patient pays the remaining deductible first; insurer pays the coverage
 *   percentage of the remainder, capped by remaining annual max
 * - deductible remaining = plan deductible minus the deductible amounts applied
 *   on this coverage's PAID claims this benefit year
 * - benefit year = clinic-local calendar year; benefits used = paid amounts on
 *   PAID/CLOSED claims
 */
@Service
@Transactional(readOnly = true)
public class EstimateService implements InsuranceEstimateApi {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PatientInsuranceRepository coverageRepository;
    private final FeeScheduleRepository feeScheduleRepository;
    private final CoverageRuleRepository coverageRuleRepository;
    private final ClaimRepository claimRepository;
    private final InsuranceAdminService adminService;
    private final ProcedureCatalogApi catalogApi;
    private final com.dentalcore.infrastructure.time.ClinicTimeService clinicTime;

    public EstimateService(PatientInsuranceRepository coverageRepository,
                           FeeScheduleRepository feeScheduleRepository,
                           CoverageRuleRepository coverageRuleRepository,
                           ClaimRepository claimRepository,
                           InsuranceAdminService adminService,
                           ProcedureCatalogApi catalogApi,
                           com.dentalcore.infrastructure.time.ClinicTimeService clinicTime) {
        this.coverageRepository = coverageRepository;
        this.feeScheduleRepository = feeScheduleRepository;
        this.coverageRuleRepository = coverageRuleRepository;
        this.claimRepository = claimRepository;
        this.adminService = adminService;
        this.catalogApi = catalogApi;
        this.clinicTime = clinicTime;
    }

    @Override
    public EstimateResult estimateFor(UUID patientId, List<EstimateItem> items) {
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                items.stream().map(EstimateItem::procedureCodeId).collect(Collectors.toSet()));

        Optional<PatientInsurance> coverageOpt = activePrimaryCoverage(patientId);
        if (coverageOpt.isEmpty()) {
            return uncovered(items, catalog);
        }
        PatientInsurance coverage = coverageOpt.get();
        InsurancePlan plan = adminService.requirePlan(coverage.getPlanId());
        String carrierName = adminService.findCarrier(plan.getCarrierId()).getName();

        Map<UUID, BigDecimal> scheduleFees = plan.getFeeScheduleId() == null
                ? Map.of()
                : feeScheduleRepository.findById(plan.getFeeScheduleId())
                .map(FeeSchedule::getFees).orElse(List.of()).stream()
                .collect(Collectors.toMap(FeeScheduleFee::getProcedureCodeId,
                        FeeScheduleFee::getFee));
        Map<String, Integer> coveragePercents =
                coverageRuleRepository.findByPlanId(plan.getId()).stream()
                        .collect(Collectors.toMap(CoverageRule::getCategory,
                                CoverageRule::getCoveragePercent));

        java.time.Instant benefitYearStart = startOfBenefitYear();
        BigDecimal benefitsUsed = claimRepository.benefitsPaidSince(
                coverage.getId(), benefitYearStart);
        BigDecimal annualMax = orZero(plan.getAnnualMax());
        BigDecimal benefitsRemaining = annualMax.signum() > 0
                ? annualMax.subtract(benefitsUsed).max(BigDecimal.ZERO)
                : null; // no annual max configured = unlimited
        BigDecimal deductible = orZero(plan.getDeductible());
        BigDecimal deductibleRemaining = deductible
                .subtract(claimRepository.deductibleAppliedSince(
                        coverage.getId(), benefitYearStart))
                .max(BigDecimal.ZERO);

        List<EstimateLine> lines = new ArrayList<>();
        BigDecimal totalInsurance = BigDecimal.ZERO;
        BigDecimal totalPatient = BigDecimal.ZERO;
        BigDecimal totalWriteOff = BigDecimal.ZERO;
        BigDecimal deductibleLeft = deductibleRemaining;
        BigDecimal maxLeft = benefitsRemaining;

        for (EstimateItem item : items) {
            ProcedureSummary entry = catalog.get(item.procedureCodeId());
            BigDecimal gross = item.grossFee() != null ? item.grossFee()
                    : entry != null ? entry.standardFee() : BigDecimal.ZERO;
            BigDecimal allowed = scheduleFees.getOrDefault(item.procedureCodeId(), gross);
            int percent = entry == null ? 0
                    : coveragePercents.getOrDefault(entry.category(), 0);

            BigDecimal deductibleApplied = BigDecimal.ZERO;
            BigDecimal insurance = BigDecimal.ZERO;
            if (percent > 0) {
                deductibleApplied = deductibleLeft.min(allowed);
                deductibleLeft = deductibleLeft.subtract(deductibleApplied);
                insurance = allowed.subtract(deductibleApplied)
                        .multiply(BigDecimal.valueOf(percent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (maxLeft != null) {
                    insurance = insurance.min(maxLeft);
                    maxLeft = maxLeft.subtract(insurance);
                }
            }
            BigDecimal patient = allowed.subtract(insurance);
            BigDecimal writeOff = gross.subtract(allowed).max(BigDecimal.ZERO);

            lines.add(new EstimateLine(
                    item.procedureCodeId(),
                    entry != null ? entry.code() : null,
                    entry != null ? entry.description() : null,
                    gross, allowed, percent, deductibleApplied, insurance, patient, writeOff));
            totalInsurance = totalInsurance.add(insurance);
            totalPatient = totalPatient.add(patient);
            totalWriteOff = totalWriteOff.add(writeOff);
        }

        return new EstimateResult(true, carrierName, plan.getPlanName(),
                deductible, deductibleRemaining, plan.getAnnualMax(), benefitsUsed,
                benefitsRemaining, lines, totalInsurance, totalPatient, totalWriteOff);
    }

    /** Fee a claim line should bill: the plan's schedule fee, else the fallback. */
    public BigDecimal allowedFeeFor(UUID planId, UUID procedureCodeId, BigDecimal fallback) {
        InsurancePlan plan = adminService.requirePlan(planId);
        if (plan.getFeeScheduleId() == null) {
            return fallback;
        }
        return feeScheduleRepository.findById(plan.getFeeScheduleId())
                .map(FeeSchedule::getFees).orElse(List.of()).stream()
                .filter(f -> f.getProcedureCodeId().equals(procedureCodeId))
                .map(FeeScheduleFee::getFee)
                .findFirst()
                .orElse(fallback);
    }

    // ---- helpers ----

    private Optional<PatientInsurance> activePrimaryCoverage(UUID patientId) {
        LocalDate today = clinicTime.today(DEFAULT_CLINIC_ID);
        return coverageRepository.findByPatientIdOrderByPriorityAsc(patientId).stream()
                .filter(c -> c.getEffectiveDate() == null || !c.getEffectiveDate().isAfter(today))
                .filter(c -> c.getTerminationDate() == null
                        || !c.getTerminationDate().isBefore(today))
                .findFirst();
    }

    private EstimateResult uncovered(List<EstimateItem> items,
                                     Map<UUID, ProcedureSummary> catalog) {
        List<EstimateLine> lines = items.stream().map(item -> {
            ProcedureSummary entry = catalog.get(item.procedureCodeId());
            BigDecimal gross = item.grossFee() != null ? item.grossFee()
                    : entry != null ? entry.standardFee() : BigDecimal.ZERO;
            return new EstimateLine(item.procedureCodeId(),
                    entry != null ? entry.code() : null,
                    entry != null ? entry.description() : null,
                    gross, gross, 0, BigDecimal.ZERO, BigDecimal.ZERO, gross, BigDecimal.ZERO);
        }).toList();
        BigDecimal totalPatient = lines.stream().map(EstimateLine::patientPortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EstimateResult(false, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, null, lines, BigDecimal.ZERO, totalPatient,
                BigDecimal.ZERO);
    }

    /** Jan 1 of the clinic-local current year, as an instant at the clinic zone. */
    java.time.Instant startOfBenefitYear() {
        java.time.ZoneId zone = clinicTime.clinicZone(DEFAULT_CLINIC_ID);
        return LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
