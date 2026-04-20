package com.dentalcore.appointments.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperatoryRequest(
        @NotBlank @Size(max = 100)
        String name,

        Boolean active
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
