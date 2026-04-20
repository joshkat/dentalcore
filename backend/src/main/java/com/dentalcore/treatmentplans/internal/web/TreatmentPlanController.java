package com.dentalcore.treatmentplans.internal.web;

import com.dentalcore.shared.web.PageResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.AddProcedureRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.CreatePlanRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.PlanResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.PlanSummaryResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdatePlanRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdatePlanStatusRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdateProcedureRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdateProcedureStatusRequest;
import com.dentalcore.treatmentplans.internal.service.TreatmentPlanService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/treatment-plans")
@Tag(name = "Treatment Plans")
public class TreatmentPlanController {

    private static final String CAN_WRITE = "hasAnyRole('ADMIN','DENTIST','HYGIENIST')";

    private final TreatmentPlanService service;
    private final com.dentalcore.insurance.api.InsuranceEstimateApi estimateApi;

    public TreatmentPlanController(TreatmentPlanService service,
                                   com.dentalcore.insurance.api.InsuranceEstimateApi estimateApi) {
        this.service = service;
        this.estimateApi = estimateApi;
    }

    @GetMapping("/{id}/estimate")
    @Operation(summary = "Insurance vs patient-portion estimate for the plan's procedures")
    public com.dentalcore.insurance.api.InsuranceEstimateApi.EstimateResult estimate(
            @PathVariable UUID id) {
        return service.estimate(id, estimateApi);
    }

    @GetMapping
    @Operation(summary = "List a patient's treatment plans")
    public PageResponse<PlanSummaryResponse> list(
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listForPatient(patientId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a treatment plan with its procedures and totals")
    public PlanResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a treatment plan")
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Rename a treatment plan / edit notes")
    public PlanResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Move a plan through its lifecycle (approval is tracked)")
    public PlanResponse updateStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdatePlanStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a DRAFT treatment plan")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ---- procedures ----

    @PostMapping("/{id}/procedures")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a procedure (fee defaults from the catalog)")
    public PlanResponse addProcedure(@PathVariable UUID id,
                                     @Valid @RequestBody AddProcedureRequest request) {
        return service.addProcedure(id, request);
    }

    @PutMapping("/{id}/procedures/{procedureId}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Edit a planned procedure (DRAFT/PRESENTED plans only)")
    public PlanResponse updateProcedure(@PathVariable UUID id, @PathVariable UUID procedureId,
                                        @Valid @RequestBody UpdateProcedureRequest request) {
        return service.updateProcedure(id, procedureId, request);
    }

    @PatchMapping("/{id}/procedures/{procedureId}/status")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Track procedure progress (APPROVED/IN_PROGRESS plans)")
    public PlanResponse updateProcedureStatus(@PathVariable UUID id, @PathVariable UUID procedureId,
                                              @Valid @RequestBody UpdateProcedureStatusRequest request) {
        return service.updateProcedureStatus(id, procedureId, request.status());
    }

    @DeleteMapping("/{id}/procedures/{procedureId}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Remove a procedure from a DRAFT/PRESENTED plan")
    public PlanResponse removeProcedure(@PathVariable UUID id, @PathVariable UUID procedureId) {
        return service.removeProcedure(id, procedureId);
    }
}
