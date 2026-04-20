package com.dentalcore.providers.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProviderRequest(
        @NotNull @Pattern(regexp = "DENTIST|HYGIENIST|ASSISTANT", message = "Unknown provider type")
        String type,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @Pattern(regexp = "\\d{10}", message = "NPI must be exactly 10 digits")
        String npi,

        @Size(max = 100) String specialty,
        @Size(max = 50) String licenseNumber,
        @Size(max = 50) String licenseState,

        @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,

        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Color must be a hex value like #3b82f6")
        String color,

        Boolean active
) {
    public String colorOrDefault() {
        return color == null ? "#3b82f6" : color;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
