package com.dentalcore.appointments.internal.web;

import com.dentalcore.appointments.internal.dto.OperatoryRequest;
import com.dentalcore.appointments.internal.dto.OperatoryResponse;
import com.dentalcore.appointments.internal.service.OperatoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operatories")
@Tag(name = "Operatories")
public class OperatoryController {

    private final OperatoryService operatoryService;

    public OperatoryController(OperatoryService operatoryService) {
        this.operatoryService = operatoryService;
    }

    @GetMapping
    @Operation(summary = "List operatories")
    public List<OperatoryResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return operatoryService.list(includeInactive);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an operatory (ADMIN only)")
    public OperatoryResponse create(@Valid @RequestBody OperatoryRequest request) {
        return operatoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rename or (de)activate an operatory (ADMIN only)")
    public OperatoryResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody OperatoryRequest request) {
        return operatoryService.update(id, request);
    }
}
