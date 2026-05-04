package com.dentalcore.procedures.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a procedure is recorded as completed. Treatment plans flip
 * the linked planned procedure to COMPLETED; the patient chart paints the
 * finished work on the tooth.
 */
public record ProcedureCompletedEvent(
        UUID completedProcedureId,
        UUID clinicId,
        UUID patientId,
        UUID providerId,
        UUID procedureCodeId,
        UUID plannedProcedureId,
        String tooth,
        String surfaces,
        Instant completedAt
) {
}
