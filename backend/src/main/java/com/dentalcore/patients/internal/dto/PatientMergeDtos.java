package com.dentalcore.patients.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs for duplicate detection and patient merge. */
public final class PatientMergeDtos {

    private PatientMergeDtos() {
    }

    /** Lightweight reference to one side of a potential duplicate pair. */
    public record DuplicatePatientRef(
            UUID patientId,
            String name,
            LocalDate dateOfBirth,
            String status
    ) {
    }

    /**
     * One potential duplicate pair. Each unordered pair appears once;
     * {@code score} is 1.0 for an exact primary-phone/email match, otherwise
     * the pg_trgm name similarity (0..1).
     */
    public record DuplicateCandidateResponse(
            DuplicatePatientRef first,
            DuplicatePatientRef second,
            double score,
            List<String> reasons
    ) {
    }

    public record MergeRequest(@NotNull UUID sourceId) {
    }

    /**
     * Outcome of a merge: how many rows were re-pointed per table, and how
     * many were skipped (left on the source) because re-pointing them would
     * have collided with a row the target already owns.
     */
    public record MergeResponse(
            UUID targetId,
            UUID sourceId,
            Map<String, Integer> repointed,
            Map<String, Integer> skipped
    ) {
    }
}
