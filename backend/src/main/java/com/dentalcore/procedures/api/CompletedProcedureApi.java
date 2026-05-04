package com.dentalcore.procedures.api;

import java.util.List;
import java.util.UUID;

/** Public completed-work interface of the procedures module. */
public interface CompletedProcedureApi {

    /**
     * Completes (and charges) every listed procedure code that has no
     * completed-procedure row for this appointment yet. Called by the
     * appointments module when a visit reaches COMPLETED, so procedures
     * already completed during checkout are never re-charged.
     */
    void completeAllForAppointment(UUID appointmentId, UUID clinicId, UUID patientId,
                                   UUID providerId, List<UUID> procedureCodeIds);

    /** Completed work recorded against an appointment (walk-out statement). */
    List<CompletedProcedureView> findForAppointment(UUID appointmentId);
}
