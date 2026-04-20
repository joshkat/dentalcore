package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MedicalAlertRequest(
        @NotNull
        @Pattern(regexp = "ALLERGY|CONDITION|ALERT|MEDICATION", message = "Unknown alert type")
        String type,

        @NotBlank @Size(max = 500)
        String description,

        @NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Unknown severity")
        String severity,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
