package com.dentalcore.billing.internal.web;

import com.dentalcore.billing.internal.dto.BillingDtos.AdjustmentRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.ChargeRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.FamilyLedgerResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerEntryResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.LedgerResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentPlanRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentPlanResponse;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentPlanStatusRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.PaymentRequest;
import com.dentalcore.billing.internal.dto.BillingDtos.ReversalRequest;
import com.dentalcore.billing.internal.service.BillingService;
import com.dentalcore.billing.internal.service.FamilyBillingService;
import com.dentalcore.billing.internal.service.PaymentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private static final String CAN_POST = "hasAuthority('BILLING_POST')";
    private static final String CAN_REVERSE = "hasAuthority('BILLING_REVERSE')";
    private static final String CAN_TAKE_PAYMENT = "hasAuthority('PAYMENTS_TAKE')";
    private static final String CAN_PRINT_STATEMENTS = "hasAuthority('STATEMENTS_GENERATE')";
    private static final String CAN_MANAGE_PLANS = "hasAuthority('PAYMENT_PLANS_MANAGE')";
    private static final String CAN_VIEW_LEDGER = "hasAuthority('BILLING_READ')";

    private final BillingService service;
    private final com.dentalcore.billing.internal.service.StatementService statementService;
    private final com.dentalcore.billing.internal.service.WalkoutService walkoutService;
    private final FamilyBillingService familyBillingService;
    private final PaymentPlanService paymentPlanService;

    public BillingController(BillingService service,
                             com.dentalcore.billing.internal.service.StatementService statementService,
                             com.dentalcore.billing.internal.service.WalkoutService walkoutService,
                             FamilyBillingService familyBillingService,
                             PaymentPlanService paymentPlanService) {
        this.service = service;
        this.statementService = statementService;
        this.walkoutService = walkoutService;
        this.familyBillingService = familyBillingService;
        this.paymentPlanService = paymentPlanService;
    }

    @GetMapping("/walkout")
    @PreAuthorize(CAN_PRINT_STATEMENTS)
    @Operation(summary = "Walk-out statement (PDF) for an appointment's visit")
    public org.springframework.http.ResponseEntity<byte[]> walkout(
            @RequestParam UUID appointmentId) {
        byte[] pdf = walkoutService.walkoutPdf(appointmentId);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment().filename("walkout-" + appointmentId + ".pdf").build());
        return new org.springframework.http.ResponseEntity<>(pdf, headers,
                org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/statement")
    @PreAuthorize(CAN_PRINT_STATEMENTS)
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
    @PreAuthorize(CAN_POST)
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
    @PreAuthorize(CAN_POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a signed adjustment (negative = credit/discount)")
    public LedgerEntryResponse postAdjustment(@Valid @RequestBody AdjustmentRequest request) {
        return service.postAdjustment(request);
    }

    @PostMapping("/entries/{id}/reverse")
    @PreAuthorize(CAN_REVERSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Void an entry with a negating reversal (entries are never edited)")
    public LedgerEntryResponse reverse(@PathVariable UUID id,
                                       @Valid @RequestBody ReversalRequest request) {
        return service.reverse(id, request.reason());
    }

    // ---- family billing (guarantor accounts) ----

    @GetMapping("/family-ledger")
    @PreAuthorize(CAN_VIEW_LEDGER)
    @Operation(summary = "Combined ledger for a guarantor and the patients who roll up to them")
    public FamilyLedgerResponse familyLedger(@RequestParam UUID guarantorId) {
        return familyBillingService.familyLedger(guarantorId);
    }

    @GetMapping("/family-statement")
    @PreAuthorize(CAN_PRINT_STATEMENTS)
    @Operation(summary = "Printable family statement (PDF) grouped by patient")
    public org.springframework.http.ResponseEntity<byte[]> familyStatement(
            @RequestParam UUID guarantorId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {
        byte[] pdf = familyBillingService.familyStatementPdf(guarantorId, from, to);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment().filename("family-statement-" + from + "-" + to + ".pdf").build());
        return new org.springframework.http.ResponseEntity<>(pdf, headers,
                org.springframework.http.HttpStatus.OK);
    }

    // ---- payment plans ----

    @PostMapping("/payment-plans")
    @PreAuthorize(CAN_MANAGE_PLANS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a payment plan with a generated installment schedule")
    public PaymentPlanResponse createPaymentPlan(@Valid @RequestBody PaymentPlanRequest request) {
        return paymentPlanService.create(request);
    }

    @GetMapping("/payment-plans")
    @PreAuthorize(CAN_VIEW_LEDGER)
    @Operation(summary = "A patient's payment plans, newest first")
    public java.util.List<PaymentPlanResponse> listPaymentPlans(@RequestParam UUID patientId) {
        return paymentPlanService.listFor(patientId);
    }

    @PatchMapping("/payment-plans/{id}/status")
    @PreAuthorize(CAN_MANAGE_PLANS)
    @Operation(summary = "Close out an ACTIVE plan (COMPLETED, DEFAULTED, or CANCELLED)")
    public PaymentPlanResponse updatePaymentPlanStatus(
            @PathVariable UUID id, @Valid @RequestBody PaymentPlanStatusRequest request) {
        return paymentPlanService.updateStatus(id, request.status());
    }
}
