package com.dentalcore.appointments.internal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SetAppointmentProceduresRequest(
        @NotNull @Size(max = 50)
        List<UUID> procedureCodeIds
) {
}
