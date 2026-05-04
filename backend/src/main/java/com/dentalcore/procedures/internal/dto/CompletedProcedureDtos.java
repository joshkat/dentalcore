package com.dentalcore.procedures.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CompletedProcedureDtos {

    private CompletedProcedureDtos() {
    }

    public record CompleteProcedureRequest(
            @NotNull UUID patientId,
            @NotNull UUID providerId,
            @NotNull UUID procedureCodeId,
            UUID appointmentId,
            UUID plannedProcedureId,
            @Size(max = 4) String tooth,
            @Size(max = 16) String surfaces,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal feeOverride,
            @Size(max = 2000) String notes
    ) {
    }

    public record CompletedProcedureResponse(
            UUID id,
            UUID patientId,
            UUID providerId,
            String providerFirstName,
            String providerLastName,
            UUID procedureCodeId,
            String code,
            String description,
            String tooth,
            String surfaces,
            BigDecimal fee,
            UUID appointmentId,
            UUID plannedProcedureId,
            Instant completedAt,
            LocalDate entryDate
    ) {
    }
}
