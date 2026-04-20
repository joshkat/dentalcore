package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.internal.dto.ChartDtos.ChartProcedureResponse;
import com.dentalcore.patients.internal.dto.ChartDtos.ChartResponse;
import com.dentalcore.patients.internal.dto.ChartDtos.ToothConditionRequest;
import com.dentalcore.patients.internal.dto.ChartDtos.ToothConditionResponse;
import com.dentalcore.patients.internal.entity.ToothCondition;
import com.dentalcore.patients.internal.repository.ToothConditionRepository;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.treatmentplans.api.TreatmentPlanApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ToothChartService {

    private final ToothConditionRepository conditionRepository;
    private final PatientService patientService;
    private final TreatmentPlanApi treatmentPlanApi;
    private final ApplicationEventPublisher events;

    public ToothChartService(ToothConditionRepository conditionRepository,
                             PatientService patientService,
                             TreatmentPlanApi treatmentPlanApi,
                             ApplicationEventPublisher events) {
        this.conditionRepository = conditionRepository;
        this.patientService = patientService;
        this.treatmentPlanApi = treatmentPlanApi;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ChartResponse chart(UUID patientId) {
        patientService.findPatient(patientId); // 404 on unknown
        List<ToothConditionResponse> conditions =
                conditionRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                        .map(this::toResponse)
                        .toList();
        List<ChartProcedureResponse> procedures =
                treatmentPlanApi.findToothProcedures(patientId).stream()
                        .map(view -> new ChartProcedureResponse(
                                view.planId(), view.planTitle(), view.planStatus(),
                                view.plannedProcedureId(), view.tooth(), view.surface(),
                                view.procedureStatus(), view.code(), view.description()))
                        .toList();
        return new ChartResponse(conditions, procedures);
    }

    public ToothConditionResponse addCondition(UUID patientId, ToothConditionRequest request) {
        patientService.findPatient(patientId);
        String tooth = ToothCondition.normalizeTooth(request.tooth());
        ToothCondition.Condition condition = ToothCondition.Condition.valueOf(request.condition());

        List<ToothCondition> active = conditionRepository
                .findByPatientIdAndToothAndStatus(patientId, tooth, ToothCondition.Status.ACTIVE);
        boolean toothMissing = active.stream()
                .anyMatch(c -> c.getCondition() == ToothCondition.Condition.MISSING);
        if (toothMissing) {
            throw new InvalidRequestException(
                    "Tooth %s is charted as missing — resolve that first".formatted(tooth));
        }
        if (condition == ToothCondition.Condition.MISSING && !active.isEmpty()) {
            throw new InvalidRequestException(
                    "Resolve the %d active condition(s) on tooth %s before marking it missing"
                            .formatted(active.size(), tooth));
        }

        ToothCondition saved = conditionRepository.save(new ToothCondition(
                patientId, tooth, request.surfaces(), condition, request.notes(),
                CurrentUser.id().orElse(null)));

        publish(patientId, Map.of("toothConditionAdded", condition.name(), "tooth", tooth));
        return toResponse(saved);
    }

    public ToothConditionResponse updateCondition(UUID patientId, UUID conditionId,
                                                  ToothConditionRequest request) {
        ToothCondition condition = findOwned(patientId, conditionId);
        condition.edit(request.surfaces(),
                ToothCondition.Condition.valueOf(request.condition()), request.notes());
        publish(patientId, Map.of("toothConditionUpdated", condition.getCondition().name(),
                "tooth", condition.getTooth()));
        return toResponse(condition);
    }

    public ToothConditionResponse resolveCondition(UUID patientId, UUID conditionId) {
        ToothCondition condition = findOwned(patientId, conditionId);
        condition.resolve();
        publish(patientId, Map.of("toothConditionResolved", condition.getCondition().name(),
                "tooth", condition.getTooth()));
        return toResponse(condition);
    }

    public void deleteCondition(UUID patientId, UUID conditionId) {
        ToothCondition condition = findOwned(patientId, conditionId);
        conditionRepository.delete(condition);
        publish(patientId, Map.of("toothConditionDeleted", condition.getCondition().name(),
                "tooth", condition.getTooth()));
    }

    private ToothCondition findOwned(UUID patientId, UUID conditionId) {
        return conditionRepository.findById(conditionId)
                .filter(c -> c.getPatientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Tooth condition", conditionId));
    }

    private void publish(UUID patientId, Map<String, Object> details) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), PatientService.ENTITY_TYPE, patientId,
                AuditEvent.AuditAction.UPDATE, null, details));
    }

    private ToothConditionResponse toResponse(ToothCondition c) {
        return new ToothConditionResponse(
                c.getId(), c.getTooth(), c.getSurfaces(), c.getCondition().name(),
                c.getStatus().name(), c.getNotes(), c.getRecordedBy(), c.getResolvedAt(),
                c.getCreatedAt());
    }
}
