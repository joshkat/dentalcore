package com.dentalcore.insurance.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InsuranceDtos {

    private InsuranceDtos() {
    }

    // ---- carriers ----

    public record CarrierRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 20) String payerId,
            @Size(max = 30) String phone,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 50) String state,
            @Size(max = 20) String postalCode
    ) {
    }

    public record CarrierResponse(
            UUID id,
            String name,
            String payerId,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            int planCount
    ) {
    }

    // ---- plans ----

    public record PlanRequest(
            @NotNull UUID carrierId,
            @NotBlank @Size(max = 200) String planName,
            @Size(max = 50) String groupNumber,
            @NotNull
            @Pattern(regexp = "PPO|HMO|INDEMNITY|MEDICAID|DISCOUNT|OTHER", message = "Unknown plan type")
            String planType,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal annualMax,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal deductible,
            @Size(max = 2000) String coverageNotes
    ) {
    }

    public record PlanResponse(
            UUID id,
            UUID carrierId,
            String carrierName,
            String planName,
            String groupNumber,
            String planType,
            BigDecimal annualMax,
            BigDecimal deductible,
            String coverageNotes,
            UUID feeScheduleId
    ) {
    }

    // ---- patient coverage ----

    public record CoverageRequest(
            @NotNull UUID patientId,
            @NotNull UUID planId,
            @NotNull UUID subscriberPatientId,
            @NotNull
            @Pattern(regexp = "SELF|SPOUSE|CHILD|OTHER", message = "Unknown relationship")
            String relationshipToSubscriber,
            @NotBlank @Size(max = 50) String memberId,
            @NotNull @Pattern(regexp = "PRIMARY|SECONDARY", message = "Unknown priority")
            String priority,
            LocalDate effectiveDate,
            LocalDate terminationDate
    ) {
    }

    public record CoverageResponse(
            UUID id,
            UUID patientId,
            UUID planId,
            String planName,
            String planType,
            String carrierName,
            UUID subscriberPatientId,
            String subscriberFirstName,
            String subscriberLastName,
            String relationshipToSubscriber,
            String memberId,
            String priority,
            LocalDate effectiveDate,
            LocalDate terminationDate
    ) {
    }

    // ---- claims ----

    public record CreateClaimRequest(
            @NotNull UUID patientInsuranceId,
            @Size(max = 2000) String notes
    ) {
    }

    public record ClaimLineRequest(
            @NotNull UUID procedureCodeId,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal billedAmount
    ) {
    }

    public record ClaimPaymentRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal paidAmount
    ) {
    }

    public record ClaimStatusRequest(
            @NotNull
            @Pattern(regexp = "DRAFT|SUBMITTED|ACCEPTED|DENIED|PAID|CLOSED", message = "Unknown status")
            String status
    ) {
    }

    public record ClaimLineResponse(
            UUID id,
            UUID procedureCodeId,
            String code,
            String description,
            BigDecimal billedAmount,
            BigDecimal paidAmount
    ) {
    }

    public record ClaimResponse(
            UUID id,
            UUID patientInsuranceId,
            UUID patientId,
            String patientFirstName,
            String patientLastName,
            String carrierName,
            String planName,
            String memberId,
            String status,
            Instant submittedAt,
            String notes,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            List<ClaimLineResponse> procedures,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
