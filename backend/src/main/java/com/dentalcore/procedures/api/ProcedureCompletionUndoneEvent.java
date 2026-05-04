package com.dentalcore.procedures.api;

import java.util.UUID;

/**
 * Published when a same-day completed procedure is undone. Treatment plans
 * revert the linked planned procedure to PLANNED.
 */
public record ProcedureCompletionUndoneEvent(UUID plannedProcedureId) {
}
