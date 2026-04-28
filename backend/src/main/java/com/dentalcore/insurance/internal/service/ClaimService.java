package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimLineRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimLineResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimPaymentRequest;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.ClaimResponse;
import com.dentalcore.insurance.internal.dto.InsuranceDtos.CreateClaimRequest;
import com.dentalcore.insurance.internal.entity.Claim;
import com.dentalcore.insurance.internal.entity.ClaimProcedure;
import com.dentalcore.insurance.internal.entity.ClaimStatus;
import com.dentalcore.insurance.internal.entity.PatientInsurance;
import com.dentalcore.insurance.internal.repository.ClaimRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.shared.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClaimService {

    private static final String ENTITY_TYPE = "Claim";

    private final ClaimRepository claimRepository;
    private final CoverageService coverageService;
    private final ProcedureCatalogApi catalogApi;
    private final PatientApi patientApi;
    private final EstimateService estimateService;
    private final InsuranceAdminService adminService;
    private final ApplicationEventPublisher events;

    public ClaimService(ClaimRepository claimRepository,
                        CoverageService coverageService,
                        ProcedureCatalogApi catalogApi,
                        PatientApi patientApi,
                        EstimateService estimateService,
                        InsuranceAdminService adminService,
                        ApplicationEventPublisher events) {
        this.claimRepository = claimRepository;
        this.coverageService = coverageService;
        this.catalogApi = catalogApi;
        this.patientApi = patientApi;
        this.estimateService = estimateService;
        this.adminService = adminService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> list(String status, UUID patientId, Pageable pageable) {
        Page<Claim> page;
        if (patientId != null) {
            page = claimRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable);
        } else if (status != null && !status.isBlank()) {
            page = claimRepository.findByStatusOrderByCreatedAtDesc(
                    ClaimStatus.valueOf(status), pageable);
        } else {
            page = claimRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(UUID id) {
        return toResponse(findClaim(id));
    }

    public ClaimResponse create(CreateClaimRequest request) {
        PatientInsurance coverage = coverageService.findCoverage(request.patientInsuranceId());
        Claim claim = new Claim(coverage.getId(), coverage.getPatientId(), request.notes());
        claim = claimRepository.save(claim);

        publish(claim.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("patientId", coverage.getPatientId().toString()));
        return toResponse(claim);
    }

    public ClaimResponse addLine(UUID claimId, ClaimLineRequest request) {
        Claim claim = findClaim(claimId);
        ProcedureSummary entry = catalogApi.findSummary(request.procedureCodeId())
                .orElseThrow(() -> new InvalidRequestException("Unknown procedure code"));
        BigDecimal billed = request.billedAmount() != null
                ? request.billedAmount()
                : estimateService.allowedFeeFor(
                        coverageService.findCoverage(claim.getPatientInsuranceId()).getPlanId(),
                        entry.id(), entry.standardFee());
        claim.addProcedure(new ClaimProcedure(entry.id(), billed));

        publish(claimId, AuditEvent.AuditAction.UPDATE,
                Map.of("lineAdded", entry.code(), "billed", billed.toString()));
        return toResponse(claim);
    }

    public ClaimResponse removeLine(UUID claimId, UUID lineId) {
        Claim claim = findClaim(claimId);
        ClaimProcedure line = findLine(claim, lineId);
        claim.removeProcedure(line);
        publish(claimId, AuditEvent.AuditAction.UPDATE,
                Map.of("lineRemoved", lineId.toString()));
        return toResponse(claim);
    }

    public ClaimResponse recordPayment(UUID claimId, UUID lineId, ClaimPaymentRequest request) {
        Claim claim = findClaim(claimId);
        if (!claim.getStatus().allowsPayments()) {
            throw new InvalidRequestException(
                    "Payments can only be recorded on ACCEPTED claims (current: %s)"
                            .formatted(claim.getStatus()));
        }
        ClaimProcedure line = findLine(claim, lineId);
        if (request.paidAmount().compareTo(line.getBilledAmount()) > 0) {
            throw new InvalidRequestException("Paid amount cannot exceed the billed amount");
        }
        line.recordPayment(request.paidAmount());

        publish(claimId, AuditEvent.AuditAction.UPDATE,
                Map.of("paymentRecorded", request.paidAmount().toString(),
                        "lineId", lineId.toString()));
        return toResponse(claim);
    }

    public ClaimResponse updateStatus(UUID claimId, String status) {
        Claim claim = findClaim(claimId);
        String previous = claim.getStatus().name();
        ClaimStatus target = ClaimStatus.valueOf(status);

        if (target == ClaimStatus.PAID) {
            claim.recordDeductibleApplied(deductibleConsumedBy(claim,
                    coverageService.findCoverage(claim.getPatientInsuranceId())));
        }
        claim.transitionTo(target);

        publish(claimId, AuditEvent.AuditAction.STATUS_CHANGE,
                Map.of("from", previous, "to", status));

        if (target == ClaimStatus.PAID
                && claim.totalPaid().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PatientInsurance coverage = coverageService.findCoverage(claim.getPatientInsuranceId());
            String carrierName = coverageService.toResponse(coverage).carrierName();
            events.publishEvent(new com.dentalcore.insurance.api.ClaimPaidEvent(
                    claim.getId(), claim.getPatientId(), carrierName, claim.totalPaid()));
        }
        return toResponse(claim);
    }

    /**
     * Cumulative deductible tracking: a claim consumes whatever is left of the
     * plan deductible for the benefit year, capped by the total allowed amount
     * of its lines (same allowed-fee logic as the estimate engine).
     */
    private BigDecimal deductibleConsumedBy(Claim claim, PatientInsurance coverage) {
        var plan = adminService.requirePlan(coverage.getPlanId());
        BigDecimal deductible = plan.getDeductible() == null
                ? BigDecimal.ZERO : plan.getDeductible();
        if (deductible.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal alreadyApplied = claimRepository.deductibleAppliedSince(
                coverage.getId(), estimateService.startOfBenefitYear());
        BigDecimal remaining = deductible.subtract(alreadyApplied).max(BigDecimal.ZERO);

        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                claim.getProcedures().stream()
                        .map(ClaimProcedure::getProcedureCodeId)
                        .collect(Collectors.toSet()));
        BigDecimal totalAllowed = claim.getProcedures().stream()
                .map(line -> {
                    ProcedureSummary entry = catalog.get(line.getProcedureCodeId());
                    BigDecimal gross = entry != null
                            ? entry.standardFee() : line.getBilledAmount();
                    return estimateService.allowedFeeFor(
                            plan.getId(), line.getProcedureCodeId(), gross);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return remaining.min(totalAllowed);
    }

    // ---- helpers ----

    private Claim findClaim(UUID id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", id));
    }

    private ClaimProcedure findLine(Claim claim, UUID lineId) {
        return claim.getProcedures().stream()
                .filter(p -> p.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Claim line", lineId));
    }

    private void publish(UUID claimId, AuditEvent.AuditAction action, Map<String, Object> details) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, claimId, action, null, details));
    }

    private ClaimResponse toResponse(Claim claim) {
        PatientInsurance coverage = coverageService.findCoverage(claim.getPatientInsuranceId());
        var planResponse = coverageService.toResponse(coverage);
        PatientSummary patient = patientApi.findSummary(claim.getPatientId()).orElse(null);

        Set<UUID> codeIds = claim.getProcedures().stream()
                .map(ClaimProcedure::getProcedureCodeId)
                .collect(Collectors.toSet());
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(codeIds);

        return new ClaimResponse(
                claim.getId(),
                claim.getPatientInsuranceId(),
                claim.getPatientId(),
                patient != null ? patient.firstName() : null,
                patient != null ? patient.lastName() : null,
                planResponse.carrierName(),
                planResponse.planName(),
                planResponse.memberId(),
                claim.getStatus().name(),
                claim.getSubmittedAt(),
                claim.getNotes(),
                claim.totalBilled(),
                claim.totalPaid(),
                claim.getProcedures().stream().map(line -> {
                    ProcedureSummary entry = catalog.get(line.getProcedureCodeId());
                    return new ClaimLineResponse(
                            line.getId(), line.getProcedureCodeId(),
                            entry != null ? entry.code() : null,
                            entry != null ? entry.description() : null,
                            line.getBilledAmount(), line.getPaidAmount());
                }).toList(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }
}
