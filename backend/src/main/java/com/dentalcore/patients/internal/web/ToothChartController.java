package com.dentalcore.patients.internal.web;

import com.dentalcore.patients.internal.dto.ChartDtos.ChartResponse;
import com.dentalcore.patients.internal.dto.ChartDtos.ToothConditionRequest;
import com.dentalcore.patients.internal.dto.ChartDtos.ToothConditionResponse;
import com.dentalcore.patients.internal.service.ToothChartService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
@Tag(name = "Tooth Chart", description = "Odontogram conditions and plan overlay")
public class ToothChartController {

    private static final String CAN_CHART = "hasAuthority('CHART_WRITE')";

    private final ToothChartService service;

    public ToothChartController(ToothChartService service) {
        this.service = service;
    }

    @GetMapping("/chart")
    @Operation(summary = "Full chart state: conditions + tooth-specific planned procedures")
    public ChartResponse chart(@PathVariable UUID patientId) {
        return service.chart(patientId);
    }

    @PostMapping("/tooth-conditions")
    @PreAuthorize(CAN_CHART)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Chart a condition on a tooth")
    public ToothConditionResponse addCondition(@PathVariable UUID patientId,
                                               @Valid @RequestBody ToothConditionRequest request) {
        return service.addCondition(patientId, request);
    }

    @PutMapping("/tooth-conditions/{conditionId}")
    @PreAuthorize(CAN_CHART)
    @Operation(summary = "Edit an active condition")
    public ToothConditionResponse updateCondition(@PathVariable UUID patientId,
                                                  @PathVariable UUID conditionId,
                                                  @Valid @RequestBody ToothConditionRequest request) {
        return service.updateCondition(patientId, conditionId, request);
    }

    @PostMapping("/tooth-conditions/{conditionId}/resolve")
    @PreAuthorize(CAN_CHART)
    @Operation(summary = "Resolve a condition (kept in history)")
    public ToothConditionResponse resolveCondition(@PathVariable UUID patientId,
                                                   @PathVariable UUID conditionId) {
        return service.resolveCondition(patientId, conditionId);
    }

    @DeleteMapping("/tooth-conditions/{conditionId}")
    @PreAuthorize(CAN_CHART)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a mistakenly charted condition")
    public void deleteCondition(@PathVariable UUID patientId, @PathVariable UUID conditionId) {
        service.deleteCondition(patientId, conditionId);
    }
}
