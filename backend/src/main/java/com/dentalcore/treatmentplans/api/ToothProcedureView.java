package com.dentalcore.treatmentplans.api;

import java.util.UUID;

/** Planned/completed work that targets a specific tooth — consumed by the odontogram. */
public record ToothProcedureView(
        UUID planId,
        String planTitle,
        String planStatus,
        UUID plannedProcedureId,
        String tooth,
        String surface,
        String procedureStatus,
        String code,
        String description
) {
}
