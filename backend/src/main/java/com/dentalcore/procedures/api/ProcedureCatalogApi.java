package com.dentalcore.procedures.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public interface of the procedures module. */
public interface ProcedureCatalogApi {

    Optional<ProcedureSummary> findSummary(UUID procedureCodeId);

    Map<UUID, ProcedureSummary> findSummaries(Set<UUID> procedureCodeIds);
}
