package com.dentalcore.billing.internal.web;

import com.dentalcore.billing.internal.dto.BillingDtos.AdjustmentRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.ChargeRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerEntryResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.ReversalRequest;
import com.dentalcore.billing.internal.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Append-only patient ledger")
public class BillingController {

    private static final String CAN_BILL = "hasAnyRole('ADMIN','BILLING')";
    private static final String CAN_TAKE_PAYMENT = "hasAnyRole('ADMIN','BILLING','FRONT_DESK')";
    private static final String CAN_VIEW_LEDGER =
            "hasAnyRole('ADMIN','BILLING','FRONT_DESK','DENTIST')";

    private final BillingService service;
    private final com.dentalcore.billing.internal.service.StatementService statementService;

    public BillingController(BillingService service,
                             com.dentalcore.billing.internal.service.StatementService statementService) {
        this.service = service;
        this.statementService = statementService;
    }

    @GetMapping("/statement")
    @PreAuthorize(CAN_TAKE_PAYMENT)
    @Operation(summary = "Printable patient statement (PDF) for a period")
    public org.springframework.http.ResponseEntity<byte[]> statement(
            @RequestParam UUID patientId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {
        byte[] pdf = statementService.statementPdf(patientId, from, to);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment().filename("statement-" + from + "-" + to + ".pdf").build());
        return new org.springframework.http.ResponseEntity<>(pdf, headers,
                org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/ledger")
    @PreAuthorize(CAN_VIEW_LEDGER)
    @Operation(summary = "A patient's ledger with running balance")
    public LedgerResponse ledger(@RequestParam UUID patientId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return service.ledgerFor(patientId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    }

    @GetMapping("/balance")
    @PreAuthorize(CAN_VIEW_LEDGER)
    @Operation(summary = "A patient's current balance (positive = patient owes)")
    public Map<String, BigDecimal> balance(@RequestParam UUID patientId) {
        return Map.of("balance", service.balanceFor(patientId));
    }

    @PostMapping("/charges")
    @PreAuthorize(CAN_BILL)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a manual charge (amount defaults to the procedure's fee)")
    public LedgerEntryResponse postCharge(@Valid @RequestBody ChargeRequest request) {
        return service.postCharge(request);
    }

    @PostMapping("/payments")
    @PreAuthorize(CAN_TAKE_PAYMENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a patient payment")
    public LedgerEntryResponse recordPayment(@Valid @RequestBody PaymentRequest request) {
        return service.recordPayment(request);
    }

    @PostMapping("/adjustments")
    @PreAuthorize(CAN_BILL)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a signed adjustment (negative = credit/discount)")
    public LedgerEntryResponse postAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return service.postAdjustment(request);
    }

    @PostMapping("/entries/{id}/reverse")
    @PreAuthorize(CAN_BILL)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Void an entry with a negating reversal (entries are never edited)")
    public LedgerEntryResponse reverse(@PathVariable UUID id,
                                       @Valid @RequestBody ReversalRequest request) {
        return service.reverse(id, request.reason());
    }
}
