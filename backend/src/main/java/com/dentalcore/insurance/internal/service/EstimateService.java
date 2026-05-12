package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.api.InsuranceEstimateApi;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.BenefitsResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.SecondaryBenefits;
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
 *
 * <p>Coordination of benefits (traditional COB): when the patient also has
 * active SECONDARY coverage, a second pass runs the same engine mechanics
 * against the secondary plan — its coverage percentages, its own cumulative
 * deductible and its own remaining annual max — but the allowed fee is the
 * PRIMARY plan's allowed fee (the secondary's fee schedule is intentionally
 * not consulted, so both passes coordinate on a single allowed amount). The
 * secondary estimate is then capped at the patient portion left after the
 * primary, so the combined benefit never exceeds the allowed fee.
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
        PlanState primary = planState(coverageOpt.get(), true);
        PlanState secondary = activeSecondaryCoverage(patientId, primary.coverage.getId())
                .map(c -> planState(c, false))
                .orElse(null);

        List<EstimateLine> lines = new ArrayList<>();
        BigDecimal totalInsurance = BigDecimal.ZERO;
        BigDecimal totalSecondary = BigDecimal.ZERO;
        BigDecimal totalPatient = BigDecimal.ZERO;
        BigDecimal totalWriteOff = BigDecimal.ZERO;

        for (EstimateItem item : items) {
            ProcedureSummary entry = catalog.get(item.procedureCodeId());
            BigDecimal gross = item.grossFee() != null ? item.grossFee()
                    : entry != null ? entry.standardFee() : BigDecimal.ZERO;
            BigDecimal allowed = primary.scheduleFees.getOrDefault(item.procedureCodeId(), gross);
            String category = entry == null ? null : entry.category();

            int percent = primary.percentFor(category);
            BigDecimal deductibleApplied = BigDecimal.ZERO;
            BigDecimal insurance = BigDecimal.ZERO;
            if (percent > 0) {
                deductibleApplied = primary.applyDeductible(allowed);
                insurance = primary.capByAnnualMax(
                        portion(allowed.subtract(deductibleApplied), percent));
            }

            // Secondary pass (traditional COB): same engine against the
            // secondary plan, on the primary's allowed fee, capped by what the
            // patient still owes after the primary.
            BigDecimal secondaryEstimate = BigDecimal.ZERO;
            if (secondary != null) {
                int secondaryPercent = secondary.percentFor(category);
                if (secondaryPercent > 0) {
                    BigDecimal secondaryBase = allowed.subtract(insurance).max(BigDecimal.ZERO);
                    BigDecimal secondaryDeductible = secondary.applyDeductible(allowed);
                    secondaryEstimate = secondary.capByAnnualMax(
                            portion(allowed.subtract(secondaryDeductible), secondaryPercent)
                                    .min(secondaryBase));
                }
            }

            BigDecimal patient = allowed.subtract(insurance).subtract(secondaryEstimate)
                    .max(BigDecimal.ZERO);
            BigDecimal writeOff = gross.subtract(allowed).max(BigDecimal.ZERO);

            lines.add(new EstimateLine(
                    item.procedureCodeId(),
                    entry != null ? entry.code() : null,
                    entry != null ? entry.description() : null,
                    gross, allowed, percent, deductibleApplied, insurance, patient, writeOff,
                    secondaryEstimate));
            totalInsurance = totalInsurance.add(insurance);
            totalSecondary = totalSecondary.add(secondaryEstimate);
            totalPatient = totalPatient.add(patient);
            totalWriteOff = totalWriteOff.add(writeOff);
        }

        return new EstimateResult(true, primary.carrierName, primary.plan.getPlanName(),
                primary.deductible, primary.deductibleRemaining, primary.plan.getAnnualMax(),
                primary.benefitsUsed, primary.benefitsRemaining, lines, totalInsurance,
                totalPatient, totalWriteOff,
                secondary != null,
                secondary != null ? secondary.carrierName : null,
                secondary != null ? secondary.plan.getPlanName() : null,
                secondary != null ? secondary.deductibleRemaining : null,
                secondary != null ? secondary.benefitsRemaining : null,
                totalSecondary);
    }

    /** Benefit summary for the benefits endpoint: estimate fields plus a nested secondary block. */
    public BenefitsResponse benefitsFor(UUID patientId) {
        EstimateResult result = estimateFor(patientId, List.of());
        SecondaryBenefits secondaryBlock = null;
        if (result.hasSecondary()) {
            PlanState secondary = activePrimaryCoverage(patientId)
                    .flatMap(primary -> activeSecondaryCoverage(patientId, primary.getId()))
                    .map(c -> planState(c, false))
                    .orElse(null);
            if (secondary != null) {
                secondaryBlock = new SecondaryBenefits(
                        secondary.carrierName, secondary.plan.getPlanName(),
                        secondary.deductible, secondary.deductibleRemaining,
                        secondary.plan.getAnnualMax(), secondary.benefitsRemaining);
            }
        }
        return new BenefitsResponse(
                result.hasCoverage(), result.carrierName(), result.planName(),
                result.deductible(), result.deductibleRemaining(), result.annualMax(),
                result.benefitsUsed(), result.benefitsRemaining(), result.lines(),
                result.totalInsurance(), result.totalPatient(), result.totalWriteOff(),
                result.hasSecondary(), result.secondaryCarrierName(),
                result.secondaryPlanName(), result.secondaryDeductibleRemaining(),
                result.secondaryBenefitsRemaining(), result.totalSecondary(),
                secondaryBlock);
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
        return activeCoverages(patientId).findFirst();
    }

    /**
     * Active SECONDARY coverage for the COB pass, excluding the coverage already
     * used as primary (relevant when a patient only carries a SECONDARY policy,
     * which the primary pass then uses).
     */
    Optional<PatientInsurance> activeSecondaryCoverage(UUID patientId, UUID primaryCoverageId) {
        return activeCoverages(patientId)
                .filter(c -> c.getPriority() == PatientInsurance.Priority.SECONDARY)
                .filter(c -> !c.getId().equals(primaryCoverageId))
                .findFirst();
    }

    private java.util.stream.Stream<PatientInsurance> activeCoverages(UUID patientId) {
        LocalDate today = clinicTime.today(DEFAULT_CLINIC_ID);
        return coverageRepository.findByPatientIdOrderByPriorityAsc(patientId).stream()
                .filter(c -> c.getEffectiveDate() == null || !c.getEffectiveDate().isAfter(today))
                .filter(c -> c.getTerminationDate() == null
                        || !c.getTerminationDate().isBefore(today));
    }

    /** Per-coverage engine state: benefit-year accumulators plus running caps. */
    private PlanState planState(PatientInsurance coverage, boolean loadFeeSchedule) {
        InsurancePlan plan = adminService.requirePlan(coverage.getPlanId());
        String carrierName = adminService.findCarrier(plan.getCarrierId()).getName();

        Map<UUID, BigDecimal> scheduleFees = !loadFeeSchedule || plan.getFeeScheduleId() == null
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

        return new PlanState(coverage, plan, carrierName, scheduleFees, coveragePercents,
                deductible, deductibleRemaining, benefitsUsed, benefitsRemaining);
    }

    private static final class PlanState {
        final PatientInsurance coverage;
        final InsurancePlan plan;
        final String carrierName;
        final Map<UUID, BigDecimal> scheduleFees;
        final Map<String, Integer> coveragePercents;
        final BigDecimal deductible;
        final BigDecimal deductibleRemaining;
        final BigDecimal benefitsUsed;
        final BigDecimal benefitsRemaining; // null = unlimited
        private BigDecimal deductibleLeft;
        private BigDecimal maxLeft;

        PlanState(PatientInsurance coverage, InsurancePlan plan, String carrierName,
                  Map<UUID, BigDecimal> scheduleFees, Map<String, Integer> coveragePercents,
                  BigDecimal deductible, BigDecimal deductibleRemaining,
                  BigDecimal benefitsUsed, BigDecimal benefitsRemaining) {
            this.coverage = coverage;
            this.plan = plan;
            this.carrierName = carrierName;
            this.scheduleFees = scheduleFees;
            this.coveragePercents = coveragePercents;
            this.deductible = deductible;
            this.deductibleRemaining = deductibleRemaining;
            this.benefitsUsed = benefitsUsed;
            this.benefitsRemaining = benefitsRemaining;
            this.deductibleLeft = deductibleRemaining;
            this.maxLeft = benefitsRemaining;
        }

        int percentFor(String category) {
            return category == null ? 0 : coveragePercents.getOrDefault(category, 0);
        }

        BigDecimal applyDeductible(BigDecimal allowed) {
            BigDecimal applied = deductibleLeft.min(allowed);
            deductibleLeft = deductibleLeft.subtract(applied);
            return applied;
        }

        BigDecimal capByAnnualMax(BigDecimal benefit) {
            if (maxLeft == null) {
                return benefit;
            }
            BigDecimal capped = benefit.min(maxLeft);
            maxLeft = maxLeft.subtract(capped);
            return capped;
        }
    }

    private static BigDecimal portion(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
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
                    gross, gross, 0, BigDecimal.ZERO, BigDecimal.ZERO, gross, BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }).toList();
        BigDecimal totalPatient = lines.stream().map(EstimateLine::patientPortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EstimateResult(false, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, null, lines, BigDecimal.ZERO, totalPatient,
                BigDecimal.ZERO, false, null, null, null, null, BigDecimal.ZERO);
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
