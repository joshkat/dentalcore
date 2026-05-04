package com.dentalcore.appointments.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID providerId,
        @NotNull UUID operatoryId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,

        @Size(max = 2000)
        String notes,

        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Color must be a hex value like #3b82f6")
        String colorOverride,

        Boolean asap
) {
    public boolean asapOrDefault() {
        return Boolean.TRUE.equals(asap);
    }
}
