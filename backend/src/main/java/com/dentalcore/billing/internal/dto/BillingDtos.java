package com.dentalcore.billing.internal.dto;

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

public final class BillingDtos {

    private BillingDtos() {
    }

    public record ChargeRequest(
            @NotNull UUID patientId,
            UUID procedureCodeId,
            @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @Size(max = 500) String description
    ) {
    }

    public record PaymentRequest(
            @NotNull UUID patientId,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
            BigDecimal amount,
            @NotNull @Pattern(regexp = "CASH|CARD|CHECK|OTHER", message = "Unknown payment method")
            String method,
            @Size(max = 500) String description
    ) {
    }

    public record AdjustmentRequest(
            @NotNull UUID patientId,
            @NotNull @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 500) String description
    ) {
    }

    public record ReversalRequest(
            @NotBlank @Size(max = 400) String reason
    ) {
    }

    public record LedgerEntryResponse(
            UUID id,
            String type,
            BigDecimal amount,
            String description,
            String method,
            UUID procedureCodeId,
            String procedureCode,
            UUID appointmentId,
            UUID claimId,
            LocalDate entryDate,
            UUID reversalOf,
            boolean reversed,
            Instant createdAt
    ) {
    }

    public record LedgerResponse(
            BigDecimal balance,
            List<LedgerEntryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    // ---- family billing (V15) ----

    public record FamilyMemberBalance(
            UUID patientId,
            String patientName,
            BigDecimal balance
    ) {
    }

    /** {@link LedgerEntryResponse} plus the family member it belongs to. */
    public record FamilyLedgerEntryResponse(
            UUID id,
            String type,
            BigDecimal amount,
            String description,
            String method,
            UUID procedureCodeId,
            String procedureCode,
            UUID appointmentId,
            UUID claimId,
            LocalDate entryDate,
            UUID reversalOf,
            boolean reversed,
            Instant createdAt,
            UUID patientId,
            String patientName
    ) {
    }

    public record FamilyLedgerResponse(
            UUID guarantorId,
            String guarantorName,
            List<FamilyMemberBalance> members,
            List<FamilyLedgerEntryResponse> entries,
            BigDecimal totalBalance
    ) {
    }

    // ---- payment plans ----

    public record PaymentPlanRequest(
            @NotNull UUID patientId,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
            BigDecimal totalAmount,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2)
            BigDecimal downPayment,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
            BigDecimal installmentAmount,
            @NotNull @Pattern(regexp = "MONTHLY|BIWEEKLY", message = "Unknown frequency")
            String frequency,
            @NotNull LocalDate firstDueDate,
            @Size(max = 2000) String notes
    ) {
    }

    public record PaymentPlanStatusRequest(
            @NotNull @Pattern(regexp = "COMPLETED|DEFAULTED|CANCELLED",
                    message = "Status must be COMPLETED, DEFAULTED, or CANCELLED")
            String status
    ) {
    }

    public record InstallmentResponse(
            LocalDate dueDate,
            BigDecimal amount
    ) {
    }

    public record PaymentPlanResponse(
            UUID id,
            UUID patientId,
            BigDecimal totalAmount,
            BigDecimal downPayment,
            BigDecimal installmentAmount,
            String frequency,
            LocalDate firstDueDate,
            String status,
            String notes,
            List<InstallmentResponse> installments,
            BigDecimal expectedToDate,
            BigDecimal receivedToDate,
            Instant createdAt
    ) {
    }
}
