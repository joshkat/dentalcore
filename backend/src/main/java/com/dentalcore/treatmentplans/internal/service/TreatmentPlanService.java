package com.dentalcore.treatmentplans.internal.service;

import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.providers.api.ProviderSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.shared.web.PageResponse;
import com.dentalcore.treatmentplans.api.ToothProcedureView;
import com.dentalcore.treatmentplans.api.TreatmentPlanApi;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.AddProcedureRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.CreatePlanRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.PlanResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.PlanSummaryResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.PlannedProcedureResponse;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdatePlanRequest;
import com.dentalcore.treatmentplans.internal.dto.TreatmentPlanDtos.UpdateProcedureRequest;
import com.dentalcore.treatmentplans.internal.entity.PlannedProcedure;
import com.dentalcore.treatmentplans.internal.entity.TreatmentPlan;
import com.dentalcore.treatmentplans.internal.entity.TreatmentPlanStatus;
import com.dentalcore.treatmentplans.internal.repository.PlannedProcedureRepository;
import com.dentalcore.treatmentplans.internal.repository.TreatmentPlanRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TreatmentPlanService implements TreatmentPlanApi {

    private static final String ENTITY_TYPE = "TreatmentPlan";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TreatmentPlanRepository planRepository;
    private final PlannedProcedureRepository plannedProcedureRepository;
    private final PatientApi patientApi;
    private final ProviderApi providerApi;
    private final ProcedureCatalogApi catalogApi;
    private final ApplicationEventPublisher events;

    public TreatmentPlanService(TreatmentPlanRepository planRepository,
                                PlannedProcedureRepository plannedProcedureRepository,
                                PatientApi patientApi,
                                ProviderApi providerApi,
                                ProcedureCatalogApi catalogApi,
                                ApplicationEventPublisher events) {
        this.planRepository = planRepository;
        this.plannedProcedureRepository = plannedProcedureRepository;
        this.patientApi = patientApi;
        this.providerApi = providerApi;
        this.catalogApi = catalogApi;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public com.dentalcore.insurance.api.InsuranceEstimateApi.EstimateResult estimate(
            UUID planId,
            com.dentalcore.insurance.api.InsuranceEstimateApi estimateApi) {
        TreatmentPlan plan = findPlan(planId);
        var items = plan.getProcedures().stream()
                .filter(p -> p.getStatus() != PlannedProcedure.Status.CANCELLED)
                .map(p -> new com.dentalcore.insurance.api.InsuranceEstimateApi.EstimateItem(
                        p.getProcedureCodeId(), p.getEstimatedCost()))
                .toList();
        return estimateApi.estimateFor(plan.getPatientId(), items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToothProcedureView> findToothProcedures(UUID patientId) {
        List<PlannedProcedure> procedures =
                plannedProcedureRepository.findToothProceduresForPatient(patientId);
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                procedures.stream().map(PlannedProcedure::getProcedureCodeId)
                        .collect(Collectors.toSet()));
        return procedures.stream().map(p -> {
            ProcedureSummary entry = catalog.get(p.getProcedureCodeId());
            TreatmentPlan plan = p.getTreatmentPlan();
            return new ToothProcedureView(
                    plan.getId(), plan.getTitle(), plan.getStatus().name(),
                    p.getId(), p.getTooth(), p.getSurface(), p.getStatus().name(),
                    entry != null ? entry.code() : null,
                    entry != null ? entry.description() : null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PlanSummaryResponse> listForPatient(UUID patientId, Pageable pageable) {
        return PageResponse.from(
                planRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID id) {
        return toResponse(findPlan(id));
    }

    public PlanResponse create(CreatePlanRequest request) {
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        if (!providerApi.existsAndActive(request.providerId())) {
            throw new InvalidRequestException("Unknown or inactive provider");
        }
        TreatmentPlan plan = new TreatmentPlan(
                DEFAULT_CLINIC_ID, request.patientId(), request.providerId(), request.title());
        plan.rename(request.title(), request.notes());
        plan = planRepository.save(plan);

        publishAudit(plan.getId(), AuditEvent.AuditAction.CREATE, null,
                Map.of("title", plan.getTitle(), "patientId", plan.getPatientId().toString()));
        return toResponse(plan);
    }

    public PlanResponse update(UUID id, UpdatePlanRequest request) {
        TreatmentPlan plan = findPlan(id);
        Map<String, Object> before = Map.of("title", plan.getTitle());
        plan.rename(request.title(), request.notes());
        publishAudit(id, AuditEvent.AuditAction.UPDATE, before,
                Map.of("title", plan.getTitle()));
        return toResponse(plan);
    }

    public PlanResponse updateStatus(UUID id, String status) {
        TreatmentPlan plan = findPlan(id);
        String previous = plan.getStatus().name();
        plan.transitionTo(TreatmentPlanStatus.valueOf(status), CurrentUser.id().orElse(null));

        publishAudit(id, AuditEvent.AuditAction.STATUS_CHANGE,
                Map.of("status", previous), Map.of("status", status));
        return toResponse(plan);
    }

    public void delete(UUID id) {
        TreatmentPlan plan = findPlan(id);
        if (plan.getStatus() != TreatmentPlanStatus.DRAFT) {
            throw new InvalidRequestException("Only DRAFT plans can be deleted");
        }
        planRepository.delete(plan); // soft delete
        publishAudit(id, AuditEvent.AuditAction.DELETE,
                Map.of("title", plan.getTitle()), null);
    }

    public PlanResponse addProcedure(UUID planId, AddProcedureRequest request) {
        TreatmentPlan plan = findPlan(planId);
        ProcedureSummary catalogEntry = catalogApi.findSummary(request.procedureCodeId())
                .filter(ProcedureSummary::active)
                .orElseThrow(() -> new InvalidRequestException("Unknown or inactive procedure code"));

        BigDecimal cost = request.estimatedCost() != null
                ? request.estimatedCost()
                : catalogEntry.standardFee();
        plan.addProcedure(new PlannedProcedure(
                catalogEntry.id(),
                blankToNull(request.tooth()),
                blankToNull(request.surface()),
                request.priorityOrDefault(),
                cost));

        publishAudit(planId, AuditEvent.AuditAction.UPDATE, null,
                Map.of("procedureAdded", catalogEntry.code(), "estimatedCost", cost.toString()));
        return toResponse(plan);
    }

    public PlanResponse updateProcedure(UUID planId, UUID procedureId,
                                        UpdateProcedureRequest request) {
        TreatmentPlan plan = findPlan(planId);
        PlannedProcedure procedure = findProcedure(plan, procedureId);
        if (!plan.getStatus().allowsProcedureEdits()) {
            throw new InvalidRequestException(
                    "Procedures can only be edited while the plan is DRAFT or PRESENTED");
        }
        procedure.updateDetails(blankToNull(request.tooth()), blankToNull(request.surface()),
                request.priority(), request.estimatedCost());
        return toResponse(plan);
    }

    public PlanResponse updateProcedureStatus(UUID planId, UUID procedureId, String status) {
        TreatmentPlan plan = findPlan(planId);
        PlannedProcedure procedure = findProcedure(plan, procedureId);
        if (plan.getStatus() != TreatmentPlanStatus.APPROVED
                && plan.getStatus() != TreatmentPlanStatus.IN_PROGRESS) {
            throw new InvalidRequestException(
                    "Procedure progress can only be tracked on APPROVED or IN_PROGRESS plans");
        }
        String previous = procedure.getStatus().name();
        procedure.changeStatus(PlannedProcedure.Status.valueOf(status));

        publishAudit(planId, AuditEvent.AuditAction.UPDATE,
                Map.of("procedureStatus", previous),
                Map.of("procedureStatus", status, "procedureId", procedureId.toString()));
        return toResponse(plan);
    }

    public PlanResponse removeProcedure(UUID planId, UUID procedureId) {
        TreatmentPlan plan = findPlan(planId);
        PlannedProcedure procedure = findProcedure(plan, procedureId);
        plan.removeProcedure(procedure);
        publishAudit(planId, AuditEvent.AuditAction.UPDATE,
                Map.of("procedureRemoved", procedureId.toString()), null);
        return toResponse(plan);
    }

    // ---- helpers ----

    private TreatmentPlan findPlan(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan", id));
    }

    private PlannedProcedure findProcedure(TreatmentPlan plan, UUID procedureId) {
        return plan.getProcedures().stream()
                .filter(p -> p.getId().equals(procedureId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Planned procedure", procedureId));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private void publishAudit(UUID planId, AuditEvent.AuditAction action,
                              Map<String, Object> before, Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, planId, action, before, after));
    }

    private PlanSummaryResponse toSummary(TreatmentPlan plan) {
        return new PlanSummaryResponse(
                plan.getId(), plan.getTitle(), plan.getStatus().name(),
                totalCost(plan), plan.getProcedures().size(), completedCount(plan),
                plan.getCreatedAt());
    }

    private PlanResponse toResponse(TreatmentPlan plan) {
        Set<UUID> codeIds = plan.getProcedures().stream()
                .map(PlannedProcedure::getProcedureCodeId)
                .collect(Collectors.toSet());
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(codeIds);
        ProviderSummary provider = providerApi.findSummary(plan.getProviderId()).orElse(null);

        return new PlanResponse(
                plan.getId(),
                plan.getPatientId(),
                plan.getProviderId(),
                provider != null ? provider.firstName() : null,
                provider != null ? provider.lastName() : null,
                plan.getTitle(),
                plan.getStatus().name(),
                plan.getNotes(),
                plan.getApprovedAt(),
                plan.getApprovedBy(),
                totalCost(plan),
                plan.getProcedures().stream()
                        .filter(p -> p.getStatus() == PlannedProcedure.Status.COMPLETED)
                        .map(PlannedProcedure::getEstimatedCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                plan.getProcedures().size(),
                completedCount(plan),
                plan.getProcedures().stream().map(p -> {
                    ProcedureSummary entry = catalog.get(p.getProcedureCodeId());
                    return new PlannedProcedureResponse(
                            p.getId(), p.getProcedureCodeId(),
                            entry != null ? entry.code() : null,
                            entry != null ? entry.description() : null,
                            p.getTooth(), p.getSurface(), p.getPriority(),
                            p.getStatus().name(), p.getEstimatedCost(), p.getCompletedAt());
                }).toList(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    private BigDecimal totalCost(TreatmentPlan plan) {
        return plan.getProcedures().stream()
                .filter(p -> p.getStatus() != PlannedProcedure.Status.CANCELLED)
                .map(PlannedProcedure::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int completedCount(TreatmentPlan plan) {
        return (int) plan.getProcedures().stream()
                .filter(p -> p.getStatus() == PlannedProcedure.Status.COMPLETED)
                .count();
    }
}
