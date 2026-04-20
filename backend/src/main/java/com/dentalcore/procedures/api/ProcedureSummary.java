package com.dentalcore.procedures.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Cross-module view of a catalog entry — used by treatment plans, scheduling, billing. */
public record ProcedureSummary(
        UUID id,
        String code,
        String description,
        String category,
        BigDecimal standardFee,
        boolean active
) {
}
