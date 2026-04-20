package com.dentalcore.appointments.internal.entity;

import java.util.Map;
import java.util.Set;

public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    NO_SHOW,
    CANCELLED;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            SCHEDULED, Set.of(CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW),
            CONFIRMED, Set.of(CHECKED_IN, CANCELLED, NO_SHOW),
            CHECKED_IN, Set.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            NO_SHOW, Set.of(),
            CANCELLED, Set.of());

    public boolean canTransitionTo(AppointmentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Active appointments block the calendar; cancelled/no-show do not. */
    public boolean blocksCalendar() {
        return this != CANCELLED && this != NO_SHOW;
    }
}
