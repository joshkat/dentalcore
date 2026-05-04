package com.dentalcore.treatmentplans.internal.service;

import com.dentalcore.procedures.api.ProcedureCompletedEvent;
import com.dentalcore.procedures.api.ProcedureCompletionUndoneEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps treatment plans in sync with completed work. Runs in the publishing
 * transaction so the plan update commits atomically with the completion (and
 * a plan in the wrong state rolls the whole completion back).
 */
@Component
public class ProcedureCompletionListener {

    private final TreatmentPlanService planService;

    public ProcedureCompletionListener(TreatmentPlanService planService) {
        this.planService = planService;
    }

    @EventListener
    @Transactional
    public void onProcedureCompleted(ProcedureCompletedEvent event) {
        if (event.plannedProcedureId() == null) {
            return;
        }
        planService.completePlannedProcedure(event.plannedProcedureId());
    }

    @EventListener
    @Transactional
    public void onProcedureCompletionUndone(ProcedureCompletionUndoneEvent event) {
        planService.revertProcedureToPlanned(event.plannedProcedureId());
    }
}
