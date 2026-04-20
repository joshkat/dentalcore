package com.dentalcore.insurance.internal.web;

import com.dentalcore.insurance.api.InsuranceEstimateApi;
import com.dentalcore.insurance.api.InsuranceEstimateApi.EstimateItem;
import com.dentalcore.insurance.api.InsuranceEstimateApi.EstimateResult;
import com.dentalcore.insurance.internal.service.FeeScheduleService;
import com.dentalcore.insurance.internal.service.FeeScheduleService.CoverageRuleEntry;
import com.dentalcore.insurance.internal.service.FeeScheduleService.FeeEntry;
import com.dentalcore.insurance.internal.service.FeeScheduleService.ScheduleDetailResponse;
import com.dentalcore.insurance.internal.service.FeeScheduleService.ScheduleRequest;
import com.dentalcore.insurance.internal.service.FeeScheduleService.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/insurance")
@Tag(name = "Insurance Estimates", description = "Fee schedules, coverage rules, estimates")
public class EstimateController {

    private static final String CAN_MANAGE = "hasAnyRole('ADMIN','BILLING')";

    private final FeeScheduleService feeScheduleService;
    private final InsuranceEstimateApi estimateApi;

    public EstimateController(FeeScheduleService feeScheduleService,
                              InsuranceEstimateApi estimateApi) {
        this.feeScheduleService = feeScheduleService;
        this.estimateApi = estimateApi;
    }

    // ---- fee schedules ----

    @GetMapping("/fee-schedules")
    @Operation(summary = "List fee schedules")
    public List<ScheduleResponse> listSchedules() {
        return feeScheduleService.list();
    }

    @GetMapping("/fee-schedules/{id}")
    @Operation(summary = "Fee schedule with its per-code fees")
    public ScheduleDetailResponse getSchedule(@PathVariable UUID id) {
        return feeScheduleService.get(id);
    }

    @PostMapping("/fee-schedules")
    @PreAuthorize(CAN_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a fee schedule (ADMIN/BILLING)")
    public ScheduleResponse createSchedule(@Valid @RequestBody ScheduleRequest request) {
        return feeScheduleService.create(request);
    }

    @PutMapping("/fee-schedules/{id}/fees")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Batch upsert fees (ADMIN/BILLING)")
    public ScheduleDetailResponse upsertFees(@PathVariable UUID id,
                                             @RequestBody List<@Valid FeeEntry> entries) {
        return feeScheduleService.upsertFees(id, entries);
    }

    @DeleteMapping("/fee-schedules/{id}/fees/{procedureCodeId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Remove one fee (ADMIN/BILLING)")
    public ScheduleDetailResponse removeFee(@PathVariable UUID id,
                                            @PathVariable UUID procedureCodeId) {
        return feeScheduleService.removeFee(id, procedureCodeId);
    }

    // ---- plan coverage configuration ----

    public record LinkScheduleRequest(UUID feeScheduleId) {
    }

    @PutMapping("/plans/{planId}/fee-schedule")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Link a plan to a fee schedule (null unlinks)")
    public void linkSchedule(@PathVariable UUID planId,
                             @RequestBody LinkScheduleRequest request) {
        feeScheduleService.linkPlanToSchedule(planId, request.feeScheduleId());
    }

    @GetMapping("/plans/{planId}/coverage-rules")
    @Operation(summary = "Coverage percentages by category")
    public List<CoverageRuleEntry> coverageRules(@PathVariable UUID planId) {
        return feeScheduleService.coverageRules(planId);
    }

    @PutMapping("/plans/{planId}/coverage-rules")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Replace coverage rules (ADMIN/BILLING)")
    public List<CoverageRuleEntry> replaceCoverageRules(
            @PathVariable UUID planId,
            @RequestBody List<@Valid CoverageRuleEntry> entries) {
        return feeScheduleService.replaceCoverageRules(planId, entries);
    }

    // ---- estimates ----

    public record EstimateRequest(@NotNull UUID patientId,
                                  @NotNull List<EstimateItem> items) {
    }

    @PostMapping("/estimate")
    @Operation(summary = "Estimate insurance vs patient portion for procedures")
    public EstimateResult estimate(@Valid @RequestBody EstimateRequest request) {
        return estimateApi.estimateFor(request.patientId(), request.items());
    }

    @GetMapping("/benefits")
    @Operation(summary = "Benefit usage summary for a patient's primary coverage")
    public EstimateResult benefits(@RequestParam UUID patientId) {
        return estimateApi.estimateFor(patientId, List.of());
    }
}
