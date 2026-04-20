package com.dentalcore.users.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320)
        String email,

        @NotBlank
        @Size(min = 12, max = 128, message = "Password must be 12-128 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotEmpty
        Set<@Pattern(regexp = "ADMIN|DENTIST|HYGIENIST|FRONT_DESK|BILLING|READ_ONLY",
                message = "Unknown role") String> roles,

        UUID clinicId
) {
}
