package com.dentalcore.forms.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FormDtos {

    private FormDtos() {
    }

    public record FieldDto(
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Field keys may only contain letters, digits, '_' and '-'")
            String key,

            @NotBlank @Size(max = 200)
            String label,

            @NotNull
            @Pattern(regexp = "TEXT|TEXTAREA|CHECKBOX|DATE|SELECT", message = "Unknown field type")
            String type,

            boolean required,

            List<@NotBlank String> options
    ) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 120)
            String name,

            @Size(max = 5_000)
            String description,

            @NotEmpty
            List<@Valid @NotNull FieldDto> fields
    ) {
    }

    public record TemplateResponse(
            UUID id,
            String name,
            String description,
            List<Map<String, Object>> fields,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record InstanceCreateRequest(
            @NotNull UUID patientId,
            @NotNull UUID templateId
    ) {
    }

    public record AnswersRequest(
            @NotNull Map<String, Object> answers
    ) {
    }

    public record SignRequest(
            @NotBlank
            String signaturePngBase64,

            @NotBlank @Size(max = 160)
            String signedByName
    ) {
    }

    public record InstanceResponse(
            UUID id,
            UUID templateId,
            String templateName,
            UUID patientId,
            String status,
            Map<String, Object> answers,
            Instant signedAt,
            String signedByName,
            UUID documentId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
