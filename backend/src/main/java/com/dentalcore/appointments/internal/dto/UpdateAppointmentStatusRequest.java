package com.dentalcore.appointments.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAppointmentStatusRequest(
        @NotNull
        @Pattern(regexp = "SCHEDULED|CONFIRMED|CHECKED_IN|IN_PROGRESS|COMPLETED|NO_SHOW|CANCELLED",
                message = "Unknown status")
        String status,

        @Size(max = 500)
        String cancelReason
) {
}
