package com.dentalcore.treatmentplans.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TreatmentPlanDtos {

    private TreatmentPlanDtos() {
    }

    public record CreatePlanRequest(
            @NotNull UUID patientId,
            @NotNull UUID providerId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes
    ) {
    }

    public record UpdatePlanRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes
    ) {
    }

    public record UpdatePlanStatusRequest(
            @NotNull
            @Pattern(regexp = "DRAFT|PRESENTED|APPROVED|IN_PROGRESS|COMPLETED|CANCELLED",
                    message = "Unknown status")
            String status
    ) {
    }

    public record AddProcedureRequest(
            @NotNull UUID procedureCodeId,
            @Size(max = 5) String tooth,
            @Size(max = 10) String surface,
            @Min(1) @Max(10) Integer priority,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal estimatedCost
    ) {
        public int priorityOrDefault() {
            return priority == null ? 1 : priority;
        }
    }

    public record UpdateProcedureRequest(
            @Size(max = 5) String tooth,
            @Size(max = 10) String surface,
            @NotNull @Min(1) @Max(10) Integer priority,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal estimatedCost
    ) {
    }

    public record UpdateProcedureStatusRequest(
            @NotNull
            @Pattern(regexp = "PLANNED|SCHEDULED|COMPLETED|CANCELLED", message = "Unknown status")
            String status
    ) {
    }

    public record PlannedProcedureResponse(
            UUID id,
            UUID procedureCodeId,
            String code,
            String description,
            String tooth,
            String surface,
            int priority,
            String status,
            BigDecimal estimatedCost,
            Instant completedAt
    ) {
    }

    public record PlanResponse(
            UUID id,
            UUID patientId,
            UUID providerId,
            String providerFirstName,
            String providerLastName,
            String title,
            String status,
            String notes,
            Instant approvedAt,
            UUID approvedBy,
            BigDecimal totalEstimatedCost,
            BigDecimal completedCost,
            int procedureCount,
            int completedCount,
            List<PlannedProcedureResponse> procedures,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PlanSummaryResponse(
            UUID id,
            String title,
            String status,
            BigDecimal totalEstimatedCost,
            int procedureCount,
            int completedCount,
            Instant createdAt
    ) {
    }
}
