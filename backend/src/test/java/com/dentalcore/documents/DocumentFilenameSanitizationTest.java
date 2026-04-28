package com.dentalcore.documents;

import com.dentalcore.documents.internal.entity.Document;
import com.dentalcore.documents.internal.repository.DocumentRepository;
import com.dentalcore.documents.internal.service.DocumentService;
import com.dentalcore.patients.api.PatientApi;
import com.dentalcore.shared.storage.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentFilenameSanitizationTest {

    private final UUID patientId = UUID.randomUUID();
    private DocumentService service;

    @BeforeEach
    void setUp() {
        DocumentRepository repository = mock(DocumentRepository.class);
        when(repository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StoragePort storage = mock(StoragePort.class);
        PatientApi patientApi = mock(PatientApi.class);
        when(patientApi.exists(patientId)).thenReturn(true);
        when(storage.exists(anyString())).thenReturn(true);
        service = new DocumentService(repository, storage, patientApi,
                mock(ApplicationEventPublisher.class));
    }

    private String uploadedFilename(String originalFilename) {
        return service.upload(patientId, "OTHER", null,
                new MockMultipartFile("file", originalFilename, "application/pdf",
                        "content".getBytes())).filename();
    }

    @Test
    void overlongNamesAreTruncatedFromTheEndKeepingTheExtension() {
        String filename = uploadedFilename("a".repeat(300) + ".pdf");
        assertThat(filename).hasSize(255);
        assertThat(filename).endsWith(".pdf");
        assertThat(filename).startsWith("aaaa");
    }

    @Test
    void traversalSequencesAreNeutralized() {
        String filename = uploadedFilename("../../etc/passwd");
        assertThat(filename).doesNotContain("/").doesNotContain("\\");
        assertThat(filename).isEqualTo(".._.._etc_passwd");

        assertThat(uploadedFilename("..\\..\\windows\\system32\\evil.pdf"))
                .doesNotContain("\\")
                .endsWith("evil.pdf");
    }

    @Test
    void missingOrBlankNamesGetAFallback() {
        assertThat(uploadedFilename(null)).isEqualTo("document");
        assertThat(uploadedFilename("   ")).isEqualTo("document");
    }
}
