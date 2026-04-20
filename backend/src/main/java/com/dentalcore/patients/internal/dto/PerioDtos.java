package com.dentalcore.patients.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PerioDtos {

    private PerioDtos() {
    }

    public record CreateExamRequest(
            UUID providerId,
            LocalDate examDate,
            @Size(max = 1000) String notes
    ) {
    }

    public record SiteMeasurement(
            @NotBlank @Size(max = 2) String tooth,
            @NotNull @Min(1) @Max(6) Integer site,
            @Min(0) @Max(20) Integer pocketDepth,
            @Min(-10) @Max(20) Integer recession,
            Boolean bleeding,
            Boolean suppuration
    ) {
    }

    public record ToothFinding(
            @NotBlank @Size(max = 2) String tooth,
            @Min(0) @Max(3) Integer mobility,
            @Min(0) @Max(4) Integer furcation
    ) {
    }

    public record SaveMeasurementsRequest(
            @Valid List<SiteMeasurement> measurements,
            @Valid List<ToothFinding> toothFindings,
            @Size(max = 1000) String notes
    ) {
        public List<SiteMeasurement> measurementsOrEmpty() {
            return measurements == null ? List.of() : measurements;
        }

        public List<ToothFinding> toothFindingsOrEmpty() {
            return toothFindings == null ? List.of() : toothFindings;
        }
    }

    public record SiteResponse(
            String tooth,
            int site,
            Integer pocketDepth,
            Integer recession,
            boolean bleeding,
            boolean suppuration
    ) {
    }

    public record ToothFindingResponse(String tooth, Integer mobility, Integer furcation) {
    }

    public record ExamResponse(
            UUID id,
            UUID patientId,
            UUID providerId,
            LocalDate examDate,
            String notes,
            List<SiteResponse> measurements,
            List<ToothFindingResponse> toothFindings
    ) {
    }

    public record ExamSummaryResponse(
            UUID id,
            LocalDate examDate,
            UUID providerId,
            int sitesRecorded,
            int bleedingSites,
            int sites4mmPlus,
            int sites6mmPlus
    ) {
    }
}
