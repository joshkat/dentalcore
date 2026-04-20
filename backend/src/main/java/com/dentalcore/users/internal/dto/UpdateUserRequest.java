package com.dentalcore.users.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotEmpty
        Set<@Pattern(regexp = "ADMIN|DENTIST|HYGIENIST|FRONT_DESK|BILLING|READ_ONLY",
                message = "Unknown role") String> roles,

        @NotNull @Pattern(regexp = "ACTIVE|DISABLED", message = "Unknown status")
        String status
) {
}
