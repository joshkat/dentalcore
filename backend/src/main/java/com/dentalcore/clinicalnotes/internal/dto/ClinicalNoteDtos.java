package com.dentalcore.clinicalnotes.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ClinicalNoteDtos {

    private ClinicalNoteDtos() {
    }

    public record NoteRequest(
            @NotNull
            @Pattern(regexp = "EXAM|PROGRESS|PROCEDURE|PHONE|OTHER", message = "Unknown note type")
            String noteType,

            @NotBlank @Size(max = 50_000)
            String body,

            UUID providerId,
            UUID appointmentId
    ) {
    }

    public record NoteResponse(
            UUID id,
            UUID patientId,
            UUID providerId,
            UUID appointmentId,
            UUID authorUserId,
            String noteType,
            String body,
            Instant signedAt,
            UUID signedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
