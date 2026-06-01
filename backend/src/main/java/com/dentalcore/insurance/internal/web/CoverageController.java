package com.dentalcore.insurance.internal.web;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.CoverageRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CoverageResponse;
import com.dentalcore.insurance.internal.service.CoverageService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-insurance")
@Tag(name = "Patient Insurance", description = "Patient coverage")
public class CoverageController {

    private static final String CAN_WRITE = "hasAuthority('COVERAGE_MANAGE')";

    private final CoverageService service;

    public CoverageController(CoverageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List a patient's coverage, primary first")
    public List<CoverageResponse> list(@RequestParam UUID patientId) {
        return service.listForPatient(patientId);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add coverage (subscriber must be a patient)")
    public CoverageResponse create(@Valid @RequestBody CoverageRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Update coverage")
    public CoverageResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody CoverageRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove coverage (soft delete)")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
