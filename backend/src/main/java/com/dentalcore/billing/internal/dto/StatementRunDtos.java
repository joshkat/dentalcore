package com.dentalcore.billing.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response records for batch statement runs. */
public final class StatementRunDtos {

    private StatementRunDtos() {
    }

    public record StatementRunRequest(
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @DecimalMin(value = "0.00", message = "minBalance must not be negative")
            @Digits(integer = 8, fraction = 2) BigDecimal minBalance
    ) {
    }

    public record StatementRunItemResponse(
            UUID guarantorPatientId,
            String guarantorName,
            BigDecimal balance,
            UUID documentId
    ) {
    }

    public record StatementRunSummaryResponse(
            UUID id,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minBalance,
            String status,
            int totalAccounts,
            BigDecimal totalAmount,
            Instant createdAt
    ) {
    }

    public record StatementRunDetailResponse(
            UUID id,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minBalance,
            String status,
            int totalAccounts,
            BigDecimal totalAmount,
            Instant createdAt,
            List<StatementRunItemResponse> items
    ) {
    }
}
