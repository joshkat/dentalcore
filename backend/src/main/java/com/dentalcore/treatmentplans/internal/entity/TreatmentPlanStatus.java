package com.dentalcore.treatmentplans.internal.entity;

import java.util.Map;
import java.util.Set;

public enum TreatmentPlanStatus {
    DRAFT,
    PRESENTED,
    APPROVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<TreatmentPlanStatus, Set<TreatmentPlanStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(PRESENTED, CANCELLED),
            PRESENTED, Set.of(APPROVED, DRAFT, CANCELLED),
            APPROVED, Set.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    public boolean canTransitionTo(TreatmentPlanStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Procedures may only be added/edited while the plan is still being shaped. */
    public boolean allowsProcedureEdits() {
        return this == DRAFT || this == PRESENTED;
    }
}
