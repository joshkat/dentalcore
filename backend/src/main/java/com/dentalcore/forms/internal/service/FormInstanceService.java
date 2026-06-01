package com.dentalcore.forms.internal.service;

import com.dentalcore.documents.api.DocumentIngestApi;
import com.dentalcore.forms.internal.dto.FormDtos.AnswersRequest;
import com.dentalcore.forms.internal.dto.FormDtos.InstanceCreateRequest;
import com.dentalcore.forms.internal.dto.FormDtos.InstanceResponse;
import com.dentalcore.forms.internal.dto.FormDtos.SignRequest;
import com.dentalcore.forms.internal.entity.FormInstance;
import com.dentalcore.forms.internal.entity.FormTemplate;
import com.dentalcore.forms.internal.repository.FormInstanceRepository;
import com.dentalcore.infrastructure.i18n.LanguageResolver;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class FormInstanceService {

    private static final String ENTITY_TYPE = "FormInstance";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int MAX_SIGNATURE_BYTES = 500 * 1024;
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final FormInstanceRepository instanceRepository;
    private final FormTemplateService templateService;
    private final FormPdfRenderer pdfRenderer;
    private final DocumentIngestApi documentIngest;
    private final PatientApi patientApi;
    private final ApplicationEventPublisher events;
    private final LanguageResolver languageResolver;

    public FormInstanceService(FormInstanceRepository instanceRepository,
                               FormTemplateService templateService,
                               FormPdfRenderer pdfRenderer,
                               DocumentIngestApi documentIngest,
                               PatientApi patientApi,
                               ApplicationEventPublisher events,
                               LanguageResolver languageResolver) {
        this.instanceRepository = instanceRepository;
        this.templateService = templateService;
        this.pdfRenderer = pdfRenderer;
        this.documentIngest = documentIngest;
        this.patientApi = patientApi;
        this.events = events;
        this.languageResolver = languageResolver;
    }

    public InstanceResponse create(InstanceCreateRequest request) {
        FormTemplate template = templateService.findTemplate(request.templateId());
        if (!template.isActive()) {
            throw new InvalidRequestException("The form template is inactive");
        }
        if (!patientApi.exists(request.patientId())) {
            throw new InvalidRequestException("Unknown patient");
        }
        FormInstance instance = instanceRepository.saveAndFlush(new FormInstance(
                DEFAULT_CLINIC_ID, template.getId(), request.patientId()));
        publishAudit(instance, AuditEvent.AuditAction.CREATE,
                Map.of("templateId", template.getId().toString(),
                        "patientId", request.patientId().toString()));
        return toResponse(instance, template);
    }

    @Transactional(readOnly = true)
    public List<InstanceResponse> listForPatient(UUID patientId) {
        return instanceRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(instance -> toResponse(instance,
                        templateService.findTemplate(instance.getTemplateId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(UUID id) {
        FormInstance instance = findInstance(id);
        return toResponse(instance, templateService.findTemplate(instance.getTemplateId()));
    }

    public InstanceResponse updateAnswers(UUID id, AnswersRequest request) {
        FormInstance instance = findInstance(id);
        FormTemplate template = templateService.findTemplate(instance.getTemplateId());
        instance.updateAnswers(request.answers(),
                allRequiredAnswered(template, request.answers()));
        publishAudit(instance, AuditEvent.AuditAction.UPDATE,
                Map.of("status", instance.getStatus().name()));
        return toResponse(instance, template);
    }

    public InstanceResponse sign(UUID id, SignRequest request, String lang) {
        FormInstance instance = findInstance(id);
        if (instance.getStatus() != FormInstance.Status.COMPLETED) {
            throw new InvalidRequestException("Only a COMPLETED form can be signed");
        }
        byte[] signaturePng = decodeSignature(request.signaturePngBase64());
        FormTemplate template = templateService.findTemplate(instance.getTemplateId());
        PatientSummary patient = patientApi.findSummary(instance.getPatientId())
                .orElseThrow(() -> new InvalidRequestException("Unknown patient"));

        // the stored PDF is rendered once: optional ?lang param, else the
        // signing user's effective export language
        String language = languageResolver.resolve(lang);
        Instant signedAt = Instant.now();
        byte[] pdf = pdfRenderer.render(
                template.getName(),
                patient.firstName() + " " + patient.lastName(),
                template.getFields(), instance.getAnswers(),
                signaturePng, request.signedByName().trim(), signedAt, language);

        UUID documentId = documentIngest.storePdf(
                instance.getPatientId(),
                template.getName() + " - signed",
                "CONSENT", pdf, CurrentUser.id().orElse(null));

        instance.sign(request.signedByName().trim(), documentId);
        publishAudit(instance, AuditEvent.AuditAction.UPDATE,
                Map.of("status", "SIGNED", "documentId", documentId.toString(),
                        "signedByName", instance.getSignedByName()));
        return toResponse(instance, template);
    }

    private boolean allRequiredAnswered(FormTemplate template, Map<String, Object> answers) {
        for (Map<String, Object> field : template.getFields()) {
            if (Boolean.TRUE.equals(field.get("required"))) {
                Object answer = answers.get(String.valueOf(field.get("key")));
                if (answer == null || String.valueOf(answer).isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private byte[] decodeSignature(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stripDataUriPrefix(base64));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Signature is not valid base64");
        }
        if (bytes.length > MAX_SIGNATURE_BYTES) {
            throw new InvalidRequestException("Signature image exceeds the 500 KB limit");
        }
        if (bytes.length < PNG_MAGIC.length || !startsWithPngMagic(bytes)) {
            throw new InvalidRequestException("Signature must be a PNG image");
        }
        return bytes;
    }

    private String stripDataUriPrefix(String base64) {
        String trimmed = base64.trim();
        if (trimmed.startsWith("data:image/png;base64,")) {
            return trimmed.substring("data:image/png;base64,".length());
        }
        return trimmed;
    }

    private boolean startsWithPngMagic(byte[] bytes) {
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private FormInstance findInstance(UUID id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form instance", id));
    }

    private void publishAudit(FormInstance instance, AuditEvent.AuditAction action,
                              Map<String, Object> after) {
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, instance.getId(), action,
                null, after));
    }

    private InstanceResponse toResponse(FormInstance instance, FormTemplate template) {
        return new InstanceResponse(
                instance.getId(), instance.getTemplateId(), template.getName(),
                instance.getPatientId(), instance.getStatus().name(), instance.getAnswers(),
                instance.getSignedAt(), instance.getSignedByName(), instance.getDocumentId(),
                instance.getCreatedAt(), instance.getUpdatedAt());
    }
}
