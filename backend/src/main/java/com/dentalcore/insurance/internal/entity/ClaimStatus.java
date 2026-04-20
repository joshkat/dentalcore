package com.dentalcore.insurance.internal.entity;

import java.util.Map;
import java.util.Set;

public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    DENIED,
    PAID,
    CLOSED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(SUBMITTED, CLOSED),
            SUBMITTED, Set.of(ACCEPTED, DENIED),
            ACCEPTED, Set.of(PAID, DENIED),
            DENIED, Set.of(SUBMITTED, CLOSED),   // resubmit after correction, or write off
            PAID, Set.of(CLOSED),
            CLOSED, Set.of());

    public boolean canTransitionTo(ClaimStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Line items can only change while the claim has not been submitted. */
    public boolean allowsLineItemEdits() {
        return this == DRAFT;
    }

    /** Payments are recorded once the carrier has accepted the claim. */
    public boolean allowsPayments() {
        return this == ACCEPTED;
    }
}
