package com.dentalcore.appointments.api;

import java.time.Instant;
import java.util.UUID;

/** Cross-module view of an appointment — used by billing (walk-out statement). */
public record AppointmentView(
        UUID id,
        UUID clinicId,
        UUID patientId,
        UUID providerId,
        Instant startsAt,
        Instant endsAt,
        String status
) {
}
