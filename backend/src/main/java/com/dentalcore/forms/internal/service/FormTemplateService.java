package com.dentalcore.forms.internal.service;

import com.dentalcore.forms.internal.dto.FormDtos.FieldDto;
import com.dentalcore.forms.internal.dto.FormDtos.TemplateRequest;
import com.dentalcore.forms.internal.dto.FormDtos.TemplateResponse;
import com.dentalcore.forms.internal.entity.FormTemplate;
import com.dentalcore.forms.internal.repository.FormInstanceRepository;
import com.dentalcore.forms.internal.repository.FormTemplateRepository;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FormTemplateService {

    private static final String ENTITY_TYPE = "FormTemplate";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FormTemplateRepository templateRepository;
    private final FormInstanceRepository instanceRepository;
    private final ApplicationEventPublisher events;

    public FormTemplateService(FormTemplateRepository templateRepository,
                               FormInstanceRepository instanceRepository,
                               ApplicationEventPublisher events) {
        this.templateRepository = templateRepository;
        this.instanceRepository = instanceRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return templateRepository.findAllByOrderByActiveDescNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public TemplateResponse create(TemplateRequest request) {
        FormTemplate template = templateRepository.saveAndFlush(new FormTemplate(
                DEFAULT_CLINIC_ID, request.name().trim(), blankToNull(request.description()),
                toFieldMaps(request.fields())));
        publishAudit(template.getId(), AuditEvent.AuditAction.CREATE,
                Map.of("name", template.getName()));
        return toResponse(template);
    }

    public TemplateResponse update(UUID id, TemplateRequest request) {
        FormTemplate template = findTemplate(id);
        template.edit(request.name().trim(), blankToNull(request.description()),
                toFieldMaps(request.fields()));
        publishAudit(id, AuditEvent.AuditAction.UPDATE, Map.of("name", template.getName()));
        return toResponse(template);
    }

    /**
     * Hard-deletes an unused template; templates that already have instances
     * are deactivated instead so existing forms stay renderable.
     */
    public void delete(UUID id) {
        FormTemplate template = findTemplate(id);
        if (instanceRepository.existsByTemplateId(id)) {
            template.deactivate();
            publishAudit(id, AuditEvent.AuditAction.UPDATE,
                    Map.of("name", template.getName(), "active", false));
        } else {
            templateRepository.delete(template);
            publishAudit(id, AuditEvent.AuditAction.DELETE,
                    Map.of("name", template.getName()));
        }
    }

    FormTemplate findTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form template", id));
    }

    /** Validates the field definitions and normalizes them into ordered maps. */
    private List<Map<String, Object>> toFieldMaps(List<FieldDto> fields) {
        Set<String> seenKeys = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>(fields.size());
        for (FieldDto field : fields) {
            if (!seenKeys.add(field.key())) {
                throw new InvalidRequestException(
                        "Duplicate field key '%s'".formatted(field.key()));
            }
            if (field.label() == null || field.label().isBlank()) {
                throw new InvalidRequestException("Every field needs a non-empty label");
            }
            boolean isSelect = "SELECT".equals(field.type());
            if (isSelect && (field.options() == null || field.options().isEmpty())) {
                throw new InvalidRequestException(
                        "SELECT field '%s' requires options".formatted(field.key()));
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", field.key());
            map.put("label", field.label().trim());
            map.put("type", field.type());
            map.put("required", field.required());
            if (isSelect) {
                map.put("options", List.copyOf(field.options()));
            }
            result.add(map);
        }
        return result;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private void publishAudit(UUID templateId, AuditEvent.AuditAction action,
                              Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, templateId, action, null, after));
    }

    TemplateResponse toResponse(FormTemplate template) {
        return new TemplateResponse(
                template.getId(), template.getName(), template.getDescription(),
                template.getFields(), template.isActive(),
                template.getCreatedAt(), template.getUpdatedAt());
    }
}
