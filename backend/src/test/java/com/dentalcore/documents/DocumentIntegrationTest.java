package com.dentalcore.documents;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIntegrationTest extends IntegrationTest {

    private static final String FRONT_EMAIL = "front-docs@clinic.test";
    private static final String READONLY_EMAIL = "readonly-docs@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private String patientId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Doc", "lastName", "Patient",
                "dateOfBirth", "1988-08-08", "sex", "FEMALE")).getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private ResponseEntity<Map<String, Object>> upload(HttpHeaders auth, String filename,
                                                       String contentType, byte[] bytes,
                                                       String category) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.set("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(auth);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(
                "/api/v1/documents?patientId=" + patientId + "&category=" + category,
                HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void uploadDownloadRoundTripPreservesContent() {
        byte[] payload = "fake pdf content %PDF-1.4".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<Map<String, Object>> uploaded =
                upload(frontDesk, "consent form.pdf", "application/pdf", payload, "CONSENT");

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uploaded.getBody().get("filename")).isEqualTo("consent form.pdf");
        assertThat(uploaded.getBody().get("category")).isEqualTo("CONSENT");
        String id = (String) uploaded.getBody().get("id");

        ResponseEntity<byte[]> downloaded = rest.exchange(
                "/api/v1/documents/" + id + "/download", HttpMethod.GET,
                new HttpEntity<>(null, readOnly), byte[].class);
        assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.getBody()).isEqualTo(payload);
        assertThat(downloaded.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(downloaded.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("consent form.pdf");
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        ResponseEntity<Map<String, Object>> response = upload(frontDesk, "evil.exe",
                "application/x-msdownload", new byte[]{1, 2, 3}, "OTHER");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void softDeletedDocumentDisappearsFromListAndDownload() {
        String id = (String) upload(frontDesk, "photo.png", "image/png",
                new byte[]{1, 2, 3, 4}, "PHOTO").getBody().get("id");

        assertThat(api.delete("/api/v1/documents/" + id, frontDesk).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> list =
                api.get("/api/v1/documents?patientId=" + patientId, frontDesk);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) list.getBody().get("content");
        assertThat(content).noneMatch(d -> id.equals(d.get("id")));

        assertThat(rest.exchange("/api/v1/documents/" + id + "/download", HttpMethod.GET,
                new HttpEntity<>(null, frontDesk), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void readOnlyCanViewAndDownloadButNotUploadOrDelete() {
        String id = (String) upload(frontDesk, "xray.png", "image/png",
                new byte[]{9, 9, 9}, "XRAY").getBody().get("id");

        assertThat(api.get("/api/v1/documents?patientId=" + patientId, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(upload(readOnly, "nope.png", "image/png", new byte[]{1}, "PHOTO")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.delete("/api/v1/documents/" + id, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownCategoryAndPatientAreRejected() {
        assertThat(upload(frontDesk, "a.png", "image/png", new byte[]{1}, "SELFIE")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(new ByteArrayResource(new byte[]{1}) {
            @Override
            public String getFilename() {
                return "a.png";
            }
        }, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(frontDesk);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/documents?patientId=00000000-0000-0000-0000-00000000dead&category=PHOTO",
                HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
