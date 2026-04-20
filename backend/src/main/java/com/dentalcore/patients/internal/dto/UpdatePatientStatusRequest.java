package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdatePatientStatusRequest(
        @NotNull @Pattern(regexp = "ACTIVE|INACTIVE|ARCHIVED", message = "Unknown status")
        String status
) {
}
