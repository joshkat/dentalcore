package com.dentalcore.billing.internal.web;

import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunDetailResponse;
import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunRequest;
import com.dentalcore.billing.internal.dto.StatementRunDtos.StatementRunSummaryResponse;
import com.dentalcore.billing.internal.service.StatementRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/statement-runs")
@Tag(name = "Statement runs", description = "Batch statement generation for guarantor accounts")
public class StatementRunController {

    private static final String CAN_RUN_STATEMENTS = "hasAuthority('STATEMENT_RUNS_MANAGE')";

    private final StatementRunService service;

    public StatementRunController(StatementRunService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(CAN_RUN_STATEMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run batch statements: file a family-statement PDF for every "
            + "guarantor account whose balance clears the minimum")
    public StatementRunDetailResponse create(@Valid @RequestBody StatementRunRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize(CAN_RUN_STATEMENTS)
    @Operation(summary = "Past statement runs, newest first (max 50)")
    public List<StatementRunSummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_RUN_STATEMENTS)
    @Operation(summary = "One statement run with its per-account items")
    public StatementRunDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
