package com.dentalcore.procedures.internal.web;

import com.dentalcore.procedures.internal.dto.CompletedProcedureDtos.CompleteProcedureRequest;
import com.dentalcore.procedures.internal.dto.CompletedProcedureDtos.CompletedProcedureResponse;
import com.dentalcore.procedures.internal.service.CompletedProcedureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/completed-procedures")
@Tag(name = "Completed Procedures", description = "Performed work and its charges")
public class CompletedProcedureController {

    private static final String CAN_COMPLETE =
            "hasAnyRole('ADMIN','DENTIST','HYGIENIST','FRONT_DESK')";

    private final CompletedProcedureService service;

    public CompletedProcedureController(CompletedProcedureService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(CAN_COMPLETE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a completed procedure (posts the charge, updates plan and chart)")
    public CompletedProcedureResponse complete(
            @Valid @RequestBody CompleteProcedureRequest request) {
        return service.complete(request);
    }

    @GetMapping
    @Operation(summary = "A patient's completed procedures, optionally limited to a date range")
    public List<CompletedProcedureResponse> list(
            @RequestParam UUID patientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(patientId, from, to);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_COMPLETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Undo a same-day completion (reverses the charge, reverts the plan)")
    public void undo(@PathVariable UUID id) {
        service.undo(id);
    }
}
