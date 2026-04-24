package com.dentalcore.documents.internal.service;

import com.dentalcore.documents.internal.entity.Document;
import com.dentalcore.documents.internal.repository.DocumentRepository;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import com.dentalcore.shared.events.AuditEvent;
import com.dentalcore.shared.security.CurrentUser;
import com.dentalcore.shared.storage.StoragePort;
import com.dentalcore.shared.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DocumentService {

    private static final String ENTITY_TYPE = "Document";
    private static final UUID DEFAULT_CLINIC_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long MAX_SIZE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff",
            "image/dicom", "application/dicom",
            "text/plain");

    private final DocumentRepository documentRepository;
    private final StoragePort storage;
    private final PatientApi patientApi;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository documentRepository,
                           StoragePort storage,
                           PatientApi patientApi,
                           ApplicationEventPublisher events) {
        this.documentRepository = documentRepository;
        this.storage = storage;
        this.patientApi = patientApi;
        this.events = events;
    }

    public record DocumentResponse(
            UUID id,
            UUID patientId,
            String category,
            String filename,
            String contentType,
            long sizeBytes,
            String notes,
            UUID uploadedBy,
            Instant createdAt
    ) {
    }

    public record DocumentContent(String filename, String contentType, long sizeBytes,
                                  InputStream stream) {
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listForPatient(UUID patientId, Pageable pageable) {
        return PageResponse.from(
                documentRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable),
                this::toResponse);
    }

    public DocumentResponse upload(UUID patientId, String category, String notes,
                                   MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No file provided");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidRequestException("File exceeds the 25 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidRequestException(
                    "Unsupported file type. Allowed: PDF, images, plain text");
        }
        if (!patientApi.exists(patientId)) {
            throw new InvalidRequestException("Unknown patient");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String storageKey = UUID.randomUUID() + extensionOf(filename);
        try (InputStream in = file.getInputStream()) {
            storage.store(storageKey, in);
        } catch (IOException e) {
            throw new InvalidRequestException("Could not read the uploaded file");
        }

        Document document = documentRepository.save(new Document(
                DEFAULT_CLINIC_ID, patientId,
                Document.Category.valueOf(category),
                filename, contentType, file.getSize(), storageKey,
                CurrentUser.id().orElse(null),
                (notes == null || notes.isBlank()) ? null : notes));

        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, document.getId(),
                AuditEvent.AuditAction.CREATE, null,
                Map.of("filename", filename, "category", category,
                        "patientId", patientId.toString())));
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentContent open(UUID id) {
        Document document = findDocument(id);
        if (!storage.exists(document.getStorageKey())) {
            throw new ResourceNotFoundException("Document content", id);
        }
        return new DocumentContent(document.getFilename(), document.getContentType(),
                document.getSizeBytes(), storage.load(document.getStorageKey()));
    }

    public void delete(UUID id) {
        Document document = findDocument(id);
        documentRepository.delete(document); // soft delete; binary kept for recovery/audit
        events.publishEvent(new AuditEvent(
                CurrentUser.id().orElse(null), ENTITY_TYPE, id,
                AuditEvent.AuditAction.DELETE,
                Map.of("filename", document.getFilename()), null));
    }

    private Document findDocument(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    private String sanitizeFilename(String original) {
        String name = (original == null || original.isBlank()) ? "document" : original;
        name = name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (name.length() <= 255) {
            return name;
        }
        // truncate the base name from the end, keeping the extension intact
        String extension = extensionOf(name);
        return name.substring(0, 255 - extension.length()) + extension;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(), document.getPatientId(), document.getCategory().name(),
                document.getFilename(), document.getContentType(), document.getSizeBytes(),
                document.getNotes(), document.getUploadedBy(), document.getCreatedAt());
    }
}
