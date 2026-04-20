package com.dentalcore.procedures.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProcedureCodeRequest(
        @NotBlank @Size(max = 20)
        String code,

        @NotBlank @Size(max = 500)
        String description,

        @NotNull
        @Pattern(regexp = "DIAGNOSTIC|PREVENTIVE|RESTORATIVE|ENDODONTICS|PERIODONTICS"
                + "|PROSTHODONTICS|ORAL_SURGERY|ORTHODONTICS|ADJUNCTIVE|OTHER",
                message = "Unknown category")
        String category,

        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal standardFee,

        @Size(max = 10)
        String cdtCode,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
