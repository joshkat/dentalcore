package com.dentalcore.insurance.internal.web;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimResponse;
import com.dentalcore.insurance.internal.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Coordination of benefits: drafting a secondary claim from a paid primary. */
@RestController
@RequestMapping("/api/v1/insurance/claims")
@Tag(name = "Claims")
public class SecondaryClaimController {

    private static final String CAN_WRITE = "hasAnyRole('ADMIN','BILLING')";

    private final ClaimService service;

    public SecondaryClaimController(ClaimService service) {
        this.service = service;
    }

    @PostMapping("/{id}/secondary")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Draft a secondary (COB) claim from a PAID primary claim")
    public ClaimResponse createSecondary(@PathVariable UUID id) {
        return service.createSecondary(id);
    }
}
