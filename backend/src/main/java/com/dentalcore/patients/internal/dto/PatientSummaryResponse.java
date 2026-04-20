package com.dentalcore.patients.internal.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PatientSummaryResponse(
        UUID id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String status,
        String primaryPhone,
        String email,
        LocalDate nextRecallDate
) {
}
