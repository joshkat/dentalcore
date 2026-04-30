package com.dentalcore.patients.internal.service;

import com.dentalcore.patients.internal.entity.ToothCondition;
import com.dentalcore.patients.internal.repository.ToothConditionRepository;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureCompletedEvent;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Paints completed tooth-specific work onto the chart: a finished filling
 * shows as a RESTORATION, a finished root canal as ROOT_CANAL, and so on.
 * Categories without a sensible chart marker are skipped silently, as is
 * anything the chart's tooth/surface validation rejects — charting is a
 * convenience and must never block a completion.
 */
@Component
public class CompletedWorkChartListener {

    private static final Logger log = LoggerFactory.getLogger(CompletedWorkChartListener.class);

    private static final Map<String, ToothCondition.Condition> CONDITION_BY_CATEGORY = Map.of(
            "RESTORATIVE", ToothCondition.Condition.RESTORATION,
            "ENDODONTICS", ToothCondition.Condition.ROOT_CANAL,
            "PROSTHODONTICS", ToothCondition.Condition.CROWN,
            "ORAL_SURGERY", ToothCondition.Condition.MISSING);

    private final ToothConditionRepository conditionRepository;
    private final ProcedureCatalogApi catalogApi;

    public CompletedWorkChartListener(ToothConditionRepository conditionRepository,
                                      ProcedureCatalogApi catalogApi) {
        this.conditionRepository = conditionRepository;
        this.catalogApi = catalogApi;
    }

    @EventListener
    @Transactional
    public void onProcedureCompleted(ProcedureCompletedEvent event) {
        if (event.tooth() == null) {
            return;
        }
        ProcedureSummary procedure = catalogApi.findSummary(event.procedureCodeId()).orElse(null);
        if (procedure == null) {
            return;
        }
        ToothCondition.Condition condition = CONDITION_BY_CATEGORY.get(procedure.category());
        if (condition == null) {
            return; // preventive/diagnostic/perio etc. leave no chart marker
        }
        try {
            String tooth = ToothCondition.normalizeTooth(event.tooth());
            String notes = "Completed " + procedure.code();
            List<ToothCondition> active = conditionRepository
                    .findByPatientIdAndToothAndStatus(
                            event.patientId(), tooth, ToothCondition.Status.ACTIVE);
            ToothCondition existing = active.stream()
                    .filter(c -> c.getCondition() == condition)
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.edit(event.surfaces(), condition, notes);
            } else {
                conditionRepository.save(new ToothCondition(
                        event.patientId(), tooth, event.surfaces(), condition, notes,
                        CurrentUser.id().orElse(null)));
            }
        } catch (InvalidRequestException e) {
            log.debug("Skipping chart paint for completed procedure {}: {}",
                    event.completedProcedureId(), e.getMessage());
        }
    }
}
