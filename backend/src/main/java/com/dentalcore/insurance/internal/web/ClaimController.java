package com.dentalcore.insurance.internal.web;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimLineRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimPaymentRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimStatusRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CreateClaimRequest;
import com.dentalcore.insurance.internal.service.ClaimService;
import com.dentalcore.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
public class ClaimController {

    private static final String CAN_WRITE = "hasAuthority('CLAIMS_MANAGE')";

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Claims worklist, filterable by status or patient")
    public PageResponse<ClaimResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(status, patientId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a claim with its line items")
    public ClaimResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a claim against a patient's coverage")
    public ClaimResponse create(@Valid @RequestBody CreateClaimRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/procedures")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a line item (billed defaults to the catalog fee; DRAFT only)")
    public ClaimResponse addLine(@PathVariable UUID id,
                                 @Valid @RequestBody ClaimLineRequest request) {
        return service.addLine(id, request);
    }

    @DeleteMapping("/{id}/procedures/{lineId}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Remove a line item (DRAFT only)")
    public ClaimResponse removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return service.removeLine(id, lineId);
    }

    @PostMapping("/{id}/procedures/{lineId}/payment")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Record a carrier payment on an ACCEPTED claim")
    public ClaimResponse recordPayment(@PathVariable UUID id, @PathVariable UUID lineId,
                                       @Valid @RequestBody ClaimPaymentRequest request) {
        return service.recordPayment(id, lineId, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Move a claim through its lifecycle")
    public ClaimResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody ClaimStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
