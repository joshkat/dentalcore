package com.dentalcore.appointments.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when an appointment reaches COMPLETED. Billing (Phase 6) posts
 * charges from this; reporting consumes it for production metrics.
 */
public record AppointmentCompletedEvent(
        UUID appointmentId,
        UUID clinicId,
        UUID patientId,
        UUID providerId,
        List<UUID> procedureCodeIds,
        Instant completedAt
) {
}
