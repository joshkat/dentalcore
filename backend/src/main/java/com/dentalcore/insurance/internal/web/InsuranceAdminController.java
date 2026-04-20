package com.dentalcore.insurance.internal.web;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.CarrierRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CarrierResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.PlanRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.PlanResponse;
import com.dentalcore.insurance.internal.service.InsuranceAdminService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insurance")
@Tag(name = "Insurance", description = "Carriers and plans")
public class InsuranceAdminController {

    private static final String CAN_MANAGE = "hasAnyRole('ADMIN','BILLING')";

    private final InsuranceAdminService service;

    public InsuranceAdminController(InsuranceAdminService service) {
        this.service = service;
    }

    @GetMapping("/carriers")
    @Operation(summary = "List/search carriers")
    public PageResponse<CarrierResponse> listCarriers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(service.listCarriers(q, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "name"))));
    }

    @PostMapping("/carriers")
    @PreAuthorize(CAN_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a carrier (ADMIN/BILLING)")
    public CarrierResponse createCarrier(@Valid @RequestBody CarrierRequest request) {
        return service.createCarrier(request);
    }

    @PutMapping("/carriers/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Update a carrier (ADMIN/BILLING)")
    public CarrierResponse updateCarrier(@PathVariable UUID id,
                                         @Valid @RequestBody CarrierRequest request) {
        return service.updateCarrier(id, request);
    }

    @GetMapping("/carriers/{carrierId}/plans")
    @Operation(summary = "List a carrier's plans")
    public List<PlanResponse> listPlans(@PathVariable UUID carrierId) {
        return service.listPlans(carrierId);
    }

    @PostMapping("/plans")
    @PreAuthorize(CAN_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a plan (ADMIN/BILLING)")
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest request) {
        return service.createPlan(request);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Update a plan (ADMIN/BILLING)")
    public PlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return service.updatePlan(id, request);
    }
}
