package com.dentalcore.patients.api;

import java.time.LocalDate;
import java.util.UUID;

/** Cross-module view of a patient — what other modules (scheduling, billing) need. */
public record PatientSummary(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String status
) {
}
