package com.dentalcore.clinicalnotes.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NoteTemplateDtos {

    private NoteTemplateDtos() {
    }

    public record NoteTemplateRequest(
            @NotBlank @Size(max = 120)
            String name,

            @NotNull
            @Pattern(regexp = "EXAM|PROGRESS|PROCEDURE|PHONE|OTHER", message = "Unknown note type")
            String noteType,

            @NotBlank @Size(max = 50_000)
            String body
    ) {
    }

    public record NoteTemplateResponse(
            UUID id,
            String name,
            String noteType,
            String body,
            boolean active,
            Instant createdAt,
            List<String> prompts
    ) {
    }
}
