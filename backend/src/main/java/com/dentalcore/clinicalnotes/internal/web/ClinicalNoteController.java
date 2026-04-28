package com.dentalcore.clinicalnotes.internal.web;

import com.dentalcore.clinicalnotes.internal.dto.ClinicalNoteDtos.NoteRequest;
import com.dentalcore.clinicalnotes.internal.dto.ClinicalNoteDtos.NoteResponse;
import com.dentalcore.clinicalnotes.internal.service.ClinicalNoteService;
import com.dentalcore.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinical-notes")
@Tag(name = "Clinical Notes", description = "Sign-once clinical documentation")
public class ClinicalNoteController {

    private static final String CAN_WRITE = "hasAnyRole('ADMIN','DENTIST','HYGIENIST')";
    private static final String CAN_READ =
            "hasAnyRole('ADMIN','DENTIST','HYGIENIST','READ_ONLY')";

    private final ClinicalNoteService service;

    public ClinicalNoteController(ClinicalNoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List a patient's clinical notes, newest first")
    public PageResponse<NoteResponse> list(
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listForPatient(patientId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a clinical note")
    public NoteResponse create(@RequestParam UUID patientId,
                               @Valid @RequestBody NoteRequest request) {
        return service.create(patientId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Edit an unsigned clinical note")
    public NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Sign a note, making it immutable")
    public NoteResponse sign(@PathVariable UUID id) {
        return service.sign(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unsigned note (signed notes are permanent)")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
