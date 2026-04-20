package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PhoneDto(
        @NotNull @Pattern(regexp = "HOME|MOBILE|WORK", message = "Unknown phone type")
        String type,

        @NotBlank @Size(max = 30)
        @Pattern(regexp = "[0-9+()\\-. ]{7,30}", message = "Invalid phone number")
        String number,

        boolean primary
) {
}
