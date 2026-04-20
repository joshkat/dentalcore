package com.dentalcore.patients.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public interface of the patients module. */
public interface PatientApi {

    Optional<PatientSummary> findSummary(UUID patientId);

    Map<UUID, PatientSummary> findSummaries(Set<UUID> patientIds);

    boolean exists(UUID patientId);
}
