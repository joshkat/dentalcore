package com.dentalcore.insurance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public estimate interface — treatment plans use this for patient-portion columns. */
public interface InsuranceEstimateApi {

    EstimateResult estimateFor(UUID patientId, List<EstimateItem> items);

    record EstimateItem(UUID procedureCodeId, BigDecimal grossFee) {
    }

    // New COB components are appended at the END of each record so the JSON
    // field order (and all existing names/positions) is unchanged for
    // primary-only consumers.
    record EstimateLine(
            UUID procedureCodeId,
            String code,
            String description,
            BigDecimal grossFee,
            BigDecimal allowedFee,
            int coveragePercent,
            BigDecimal deductibleApplied,
            BigDecimal insuranceEstimate,
            BigDecimal patientPortion,
            BigDecimal writeOff,
            BigDecimal secondaryEstimate
    ) {
    }

    record EstimateResult(
            boolean hasCoverage,
            String carrierName,
            String planName,
            BigDecimal deductible,
            BigDecimal deductibleRemaining,
            BigDecimal annualMax,
            BigDecimal benefitsUsed,
            BigDecimal benefitsRemaining,
            List<EstimateLine> lines,
            BigDecimal totalInsurance,
            BigDecimal totalPatient,
            BigDecimal totalWriteOff,
            boolean hasSecondary,
            String secondaryCarrierName,
            String secondaryPlanName,
            BigDecimal secondaryDeductibleRemaining,
            BigDecimal secondaryBenefitsRemaining,
            BigDecimal totalSecondary
    ) {
    }
}
