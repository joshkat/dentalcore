package com.dentalcore.treatmentplans.api;

import java.util.List;
import java.util.UUID;

/** Public interface of the treatment-plans module. */
public interface TreatmentPlanApi {

    /** All tooth-specific procedures across a patient's non-cancelled plans. */
    List<ToothProcedureView> findToothProcedures(UUID patientId);
}
