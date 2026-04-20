package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record FamilyLinkRequest(
        @NotNull
        UUID relatedPatientId,

        @NotNull
        @Pattern(regexp = "GUARANTOR|SPOUSE|CHILD|PARENT|SIBLING|OTHER",
                message = "Unknown relationship")
        String relationship
) {
}
