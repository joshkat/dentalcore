package com.dentalcore.procedures.internal.web;

import com.dentalcore.procedures.internal.dto.ProcedureCodeRequest;
import com.dentalcore.procedures.internal.dto.ProcedureCodeResponse;
import com.dentalcore.procedures.internal.service.ProcedureCodeService;
import com.dentalcore.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/procedure-codes")
@Tag(name = "Procedure Codes", description = "Procedure catalog (CDT-ready)")
public class ProcedureCodeController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ProcedureCodeService service;

    public ProcedureCodeController(ProcedureCodeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the procedure catalog")
    public PageResponse<ProcedureCodeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "code"));
        return PageResponse.from(service.list(q, includeInactive, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a procedure code")
    public ProcedureCodeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a procedure to the catalog (ADMIN only)")
    public ProcedureCodeResponse create(@Valid @RequestBody ProcedureCodeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a catalog entry (ADMIN only)")
    public ProcedureCodeResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody ProcedureCodeRequest request) {
        return service.update(id, request);
    }
}
