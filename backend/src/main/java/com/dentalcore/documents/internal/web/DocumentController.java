package com.dentalcore.documents.internal.web;

import com.dentalcore.documents.internal.service.DocumentService;
import com.dentalcore.documents.internal.service.DocumentService.DocumentContent;
import com.dentalcore.documents.internal.service.DocumentService.DocumentResponse;
import com.dentalcore.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Documents")
public class DocumentController {

    private static final String CAN_WRITE =
            "hasAnyRole('ADMIN','DENTIST','HYGIENIST','FRONT_DESK')";

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List a patient's documents, newest first")
    public PageResponse<DocumentResponse> list(@RequestParam UUID patientId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return service.listForPatient(patientId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a document (PDF/image/text, max 25 MB)")
    public DocumentResponse upload(
            @RequestParam UUID patientId,
            @RequestParam @NotNull
            @Pattern(regexp = "XRAY|PHOTO|CONSENT|INSURANCE|REFERRAL|OTHER",
                    message = "Unknown category")
            String category,
            @RequestParam(required = false) String notes,
            @RequestPart("file") MultipartFile file) {
        return service.upload(patientId, category, notes, file);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download document content")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        DocumentContent content = service.open(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(content.filename())
                .build());
        // protects against content sniffing on inline previews
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(new InputStreamResource(content.stream()), headers,
                HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a document")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
