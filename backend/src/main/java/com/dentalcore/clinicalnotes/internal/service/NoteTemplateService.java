package com.dentalcore.clinicalnotes.internal.service;

import com.dentalcore.clinicalnotes.internal.dto.NoteTemplateDtos.NoteTemplateRequest;
import com.dentalcore.clinicalnotes.internal.dto.NoteTemplateDtos.NoteTemplateResponse;
import com.dentalcore.clinicalnotes.internal.entity.ClinicalNote;
import com.dentalcore.clinicalnotes.internal.entity.NoteTemplate;
import com.dentalcore.clinicalnotes.internal.repository.NoteTemplateRepository;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class NoteTemplateService {

    private static final String ENTITY_TYPE = "NoteTemplate";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final NoteTemplateRepository templateRepository;
    private final ApplicationEventPublisher events;

    public NoteTemplateService(NoteTemplateRepository templateRepository,
                               ApplicationEventPublisher events) {
        this.templateRepository = templateRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<NoteTemplateResponse> list() {
        return templateRepository.findAllByOrderByActiveDescNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public NoteTemplateResponse create(NoteTemplateRequest request) {
        // saveAndFlush: @CreationTimestamp only populates on flush and the
        // response is built before commit
        NoteTemplate template = templateRepository.saveAndFlush(new NoteTemplate(
                DEFAULT_CLINIC_ID, request.name().trim(),
                ClinicalNote.NoteType.valueOf(request.noteType()), request.body()));
        publishAudit(template.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("name", template.getName()));
        return toResponse(template);
    }

    public NoteTemplateResponse update(UUID id, NoteTemplateRequest request) {
        NoteTemplate template = findTemplate(id);
        template.edit(request.name().trim(),
                ClinicalNote.NoteType.valueOf(request.noteType()), request.body());
        publishAudit(id, AuditEvent.AuditAction.UPDATE, Map.of("name", template.getName()));
        return toResponse(template);
    }

    public void delete(UUID id) {
        NoteTemplate template = findTemplate(id);
        templateRepository.delete(template);
        publishAudit(id, AuditEvent.AuditAction.DELETE, Map.of("name", template.getName()));
    }

    /** Extracts {{placeholder}} keys, order-preserved and de-duplicated. */
    static List<String> extractPrompts(String body) {
        Set<String> prompts = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(body);
        while (matcher.find()) {
            prompts.add(matcher.group(1));
        }
        return new ArrayList<>(prompts);
    }

    private NoteTemplate findTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note template", id));
    }

    private void publishAudit(UUID templateId, AuditEvent.AuditAction action,
                              Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, templateId, action, null, after));
    }

    private NoteTemplateResponse toResponse(NoteTemplate template) {
        return new NoteTemplateResponse(
                template.getId(), template.getName(), template.getNoteType().name(),
                template.getBody(), template.isActive(), template.getCreatedAt(),
                extractPrompts(template.getBody()));
    }
}
