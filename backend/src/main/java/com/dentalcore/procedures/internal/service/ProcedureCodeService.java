package com.dentalcore.procedures.internal.service;

import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.procedures.internal.dto.ProcedureCodeRequest;
import com.dentalcore.procedures.internal.dto.ProcedureCodeResponse;
import com.dentalcore.procedures.internal.entity.ProcedureCode;
import com.dentalcore.procedures.internal.repository.ProcedureCodeRepository;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProcedureCodeService implements ProcedureCatalogApi {

    private static final String ENTITY_TYPE = "ProcedureCode";

    private final ProcedureCodeRepository repository;
    private final ApplicationEventPublisher events;

    public ProcedureCodeService(ProcedureCodeRepository repository,
                                ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<ProcedureCodeResponse> list(String q, boolean includeInactive, Pageable pageable) {
        Page<ProcedureCode> page;
        if (q != null && !q.isBlank()) {
            page = repository.search(q.trim(), pageable);
        } else if (includeInactive) {
            page = repository.findAll(pageable);
        } else {
            page = repository.findByActiveTrue(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProcedureCodeResponse get(UUID id) {
        return toResponse(findCode(id));
    }

    public ProcedureCodeResponse create(ProcedureCodeRequest request) {
        requireUniqueCode(request.code(), null);
        ProcedureCode code = new ProcedureCode(
                request.code().toUpperCase(),
                request.description(),
                ProcedureCode.Category.valueOf(request.category()),
                request.standardFee(),
                blankToNull(request.cdtCode()));
        code = repository.save(code);

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, code.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("code", code.getCode(), "fee", code.getStandardFee().toString())));
        return toResponse(code);
    }

    public ProcedureCodeResponse update(UUID id, ProcedureCodeRequest request) {
        ProcedureCode code = findCode(id);
        requireUniqueCode(request.code(), id);
        Map<String, Object> before = Map.of(
                "code", code.getCode(),
                "description", code.getDescription(),
                "fee", code.getStandardFee().toString(),
                "active", code.isActive());

        code.update(request.code().toUpperCase(), request.description(),
                ProcedureCode.Category.valueOf(request.category()),
                request.standardFee(), blankToNull(request.cdtCode()),
                request.activeOrDefault());

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.UPDATE, before,
                Map.of("code", code.getCode(), "description", code.getDescription(),
                        "fee", code.getStandardFee().toString(), "active", code.isActive())));
        return toResponse(code);
    }

    // ---- ProcedureCatalogApi ----

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcedureSummary> findSummary(UUID procedureCodeId) {
        return repository.findById(procedureCodeId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProcedureSummary> findSummaries(Set<UUID> procedureCodeIds) {
        return repository.findAllById(procedureCodeIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(ProcedureSummary::id, s -> s));
    }

    // ---- helpers ----

    private void requireUniqueCode(String code, UUID selfId) {
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ConflictException("A procedure with this code already exists");
                });
    }

    private ProcedureCode findCode(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure code", id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private ProcedureSummary toSummary(ProcedureCode code) {
        return new ProcedureSummary(code.getId(), code.getCode(), code.getDescription(),
                code.getCategory().name(), code.getStandardFee(), code.isActive());
    }

    private ProcedureCodeResponse toResponse(ProcedureCode code) {
        return new ProcedureCodeResponse(code.getId(), code.getCode(), code.getDescription(),
                code.getCategory().name(), code.getStandardFee(), code.getCdtCode(),
                code.isActive());
    }
}
