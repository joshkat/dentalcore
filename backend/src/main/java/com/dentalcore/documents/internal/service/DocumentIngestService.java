package com.dentalcore.documents.internal.service;

import com.dentalcore.documents.api.DocumentIngestApi;
import com.dentalcore.documents.internal.entity.Document;
import com.dentalcore.documents.internal.repository.DocumentRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.storage.StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the public {@link DocumentIngestApi}: files server-generated
 * PDFs through the same save path as uploads so they appear in the patient's
 * Documents tab.
 */
@Service
@Transactional
public class DocumentIngestService implements DocumentIngestApi {

    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DocumentRepository documentRepository;
    private final StoragePort storage;
    private final PatientApi patientApi;
    private final ApplicationEventPublisher events;

    public DocumentIngestService(DocumentRepository documentRepository,
                                 StoragePort storage,
                                 PatientApi patientApi,
                                 ApplicationEventPublisher events) {
        this.documentRepository = documentRepository;
        this.storage = storage;
        this.patientApi = patientApi;
        this.events = events;
    }

    @Override
    public UUID storePdf(UUID patientId, String title, String category, byte[] pdf,
                         UUID uploadedByUserId) {
        if (pdf == null || pdf.length == 0) {
            throw new InvalidRequestException("No PDF content provided");
        }
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }
        Document.Category documentCategory;
        try {
            documentCategory = Document.Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown document category");
        }

        String filename = filenameFor(title);
        String storageKey = UUID.randomUUID() + ".pdf";
        storage.store(storageKey, new ByteArrayInputStream(pdf));

        Document document = documentRepository.save(new Document(
                DEFAULT_CLINIC_ID, patientId, documentCategory,
                filename, "application/pdf", pdf.length, storageKey,
                uploadedByUserId, null));

        events.publishEvent(new AuditEvent(
                uploadedByUserId, "Document", document.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("filename", filename, "category", category,
                        "patientId", patientId.toString())));
        return document.getId();
    }

    private String filenameFor(String title) {
        String base = (title == null || title.isBlank()) ? "document" : title;
        base = base.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (base.length() > 200) {
            base = base.substring(0, 200);
        }
        return base + ".pdf";
    }
}
