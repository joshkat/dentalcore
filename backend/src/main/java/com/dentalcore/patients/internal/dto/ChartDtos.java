package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChartDtos {

    private ChartDtos() {
    }

    public record ToothConditionRequest(
            @NotBlank @Size(max = 2)
            String tooth,

            @Size(max = 6)
            @Pattern(regexp = "[MODBLImodbli]*", message = "Surfaces may only contain M, O, D, B, L, I")
            String surfaces,

            @NotNull
            @Pattern(regexp = "MISSING|CARIES|RESTORATION|CROWN|ROOT_CANAL|IMPLANT|BRIDGE"
                    + "|VENEER|SEALANT|EXTRACTION_PLANNED|FRACTURE|WATCH|OTHER",
                    message = "Unknown condition")
            String condition,

            @Size(max = 500)
            String notes
    ) {
    }

    public record ToothConditionResponse(
            UUID id,
            String tooth,
            String surfaces,
            String condition,
            String status,
            String notes,
            UUID recordedBy,
            Instant resolvedAt,
            Instant createdAt
    ) {
    }

    public record ChartProcedureResponse(
            UUID planId,
            String planTitle,
            String planStatus,
            UUID plannedProcedureId,
            String tooth,
            String surface,
            String procedureStatus,
            String code,
            String description
    ) {
    }

    public record ChartResponse(
            List<ToothConditionResponse> conditions,
            List<ChartProcedureResponse> procedures
    ) {
    }
}
