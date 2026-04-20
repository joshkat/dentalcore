package com.dentalcore.audit.internal.service;

import com.dentalcore.audit.api.AuditApi;
import com.dentalcore.audit.api.AuditEntry;
import com.dentalcore.audit.internal.dto.AuditLogResponse;
import com.dentalcore.audit.internal.entity.AuditLog;
import com.dentalcore.audit.internal.repository.AuditLogRepository;
import com.dentalcore.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditQueryService implements AuditApi {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AuditLogResponse> search(String entityType, UUID entityId, UUID userId,
                                                 Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PageResponse.from(repository.findAll(spec, pageable), this::toResponse);
    }

    @Override
    public List<AuditEntry> findByEntity(String entityType, UUID entityId, int limit) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("entityType"), entityType),
                cb.equal(root.get("entityId"), entityId));
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return repository.findAll(spec, pageable)
                .map(log -> new AuditEntry(
                        log.getId(), log.getUserId(), log.getEntityType(), log.getEntityId(),
                        log.getAction(), log.getPreviousValue(), log.getNewValue(),
                        log.getOccurredAt()))
                .getContent();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getUserId(), log.getEntityType(), log.getEntityId(),
                log.getAction(), log.getPreviousValue(), log.getNewValue(),
                log.getIpAddress(), log.getOccurredAt());
    }
}
