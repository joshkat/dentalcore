package com.dentalcore.procedures.internal.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcedureCodeResponse(
        UUID id,
        String code,
        String description,
        String category,
        BigDecimal standardFee,
        String cdtCode,
        boolean active
) {
}
