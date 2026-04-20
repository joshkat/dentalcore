package com.dentalcore.audit.api;

import java.util.List;
import java.util.UUID;

/**
 * Public read interface of the audit module. Lets other modules build
 * entity timelines without touching audit internals.
 */
public interface AuditApi {

    /** Most recent entries for an entity, newest first. */
    List<AuditEntry> findByEntity(String entityType, UUID entityId, int limit);
}
