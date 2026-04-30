package com.dentalcore.procedures.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cross-module view of a completed procedure — used by billing (walk-out). */
public record CompletedProcedureView(
        UUID id,
        UUID patientId,
        UUID providerId,
        UUID procedureCodeId,
        String code,
        String description,
        String tooth,
        String surfaces,
        BigDecimal fee,
        Instant completedAt,
        LocalDate entryDate
) {
}
