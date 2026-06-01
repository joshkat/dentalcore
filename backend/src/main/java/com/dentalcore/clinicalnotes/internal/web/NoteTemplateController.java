package com.dentalcore.clinicalnotes.internal.web;

import com.dentalcore.clinicalnotes.internal.dto.NoteTemplateDtos.NoteTemplateRequest;
import com.dentalcore.clinicalnotes.internal.dto.NoteTemplateDtos.NoteTemplateResponse;
import com.dentalcore.clinicalnotes.internal.service.NoteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinical-notes/templates")
@Tag(name = "Note Templates", description = "Reusable clinical note bodies with placeholders")
public class NoteTemplateController {

    private static final String CAN_WRITE = "hasAuthority('NOTE_TEMPLATES_MANAGE')";

    private final NoteTemplateService service;

    public NoteTemplateController(NoteTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List note templates (active first, then by name)")
    public List<NoteTemplateResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a note template")
    public NoteTemplateResponse create(@Valid @RequestBody NoteTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update a note template")
    public NoteTemplateResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody NoteTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a note template")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
