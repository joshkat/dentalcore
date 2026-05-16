package com.dentalcore.forms;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FormInstanceIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-forminst@clinic.test";
    private static final String FRONTDESK_EMAIL = "frontdesk-forminst@clinic.test";
    private static final String READONLY_EMAIL = "readonly-forminst@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private HttpHeaders admin;
    private HttpHeaders frontDesk;
    private HttpHeaders readOnly;
    private String patientId;
    private String templateId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(FRONTDESK_EMAIL, "FRONT_DESK");
        seedUser(READONLY_EMAIL, "READ_ONLY");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        frontDesk = api.login(FRONTDESK_EMAIL, PASSWORD);
        readOnly = api.login(READONLY_EMAIL, PASSWORD);

        patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Signa", "lastName", "Ture" + SEQ.incrementAndGet(),
                "dateOfBirth", "1980-02-02", "sex", "FEMALE")).getBody().get("id");
        templateId = (String) api.post("/api/v1/forms/templates", admin, Map.of(
                "name", "Consent Form " + SEQ.incrementAndGet(),
                "description", "Treatment consent",
                "fields", List.of(
                        Map.of("key", "allergies", "label", "Known allergies",
                                "type", "TEXT", "required", true),
                        Map.of("key", "smoker", "label", "Do you smoke?",
                                "type", "SELECT", "required", true,
                                "options", List.of("Yes", "No")),
                        Map.of("key", "comments", "label", "Comments",
                                "type", "TEXTAREA", "required", false))))
                .getBody().get("id");
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String createInstance() {
        ResponseEntity<Map<String, Object>> response = api.post("/api/v1/forms/instances",
                frontDesk, Map.of("patientId", patientId, "templateId", templateId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(response.getBody().get("templateName")).isNotNull();
        return (String) response.getBody().get("id");
    }

    private ResponseEntity<Map<String, Object>> putAnswers(String id, Map<String, Object> answers) {
        return api.put("/api/v1/forms/instances/" + id + "/answers", frontDesk,
                Map.of("answers", answers));
    }

    private static String signaturePngBase64() {
        try {
            BufferedImage image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 240, 80);
            g.setColor(Color.BLACK);
            g.drawLine(10, 60, 110, 20);
            g.drawLine(110, 20, 230, 55);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void lifecycleDraftToCompletedToSigned() {
        String id = createInstance();

        // partial answers keep the instance in DRAFT
        ResponseEntity<Map<String, Object>> partial =
                putAnswers(id, Map.of("allergies", "Penicillin"));
        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(partial.getBody().get("status")).isEqualTo("DRAFT");

        // all required answers flip it to COMPLETED
        ResponseEntity<Map<String, Object>> complete = putAnswers(id, Map.of(
                "allergies", "Penicillin", "smoker", "No", "comments", "None"));
        assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(complete.getBody().get("status")).isEqualTo("COMPLETED");

        // blanking a required answer flips it back to DRAFT
        Map<String, Object> blanked = new HashMap<>();
        blanked.put("allergies", " ");
        blanked.put("smoker", "No");
        assertThat(putAnswers(id, blanked).getBody().get("status")).isEqualTo("DRAFT");
        putAnswers(id, Map.of("allergies", "Penicillin", "smoker", "No"));

        // sign it
        ResponseEntity<Map<String, Object>> signed = api.post(
                "/api/v1/forms/instances/" + id + "/sign", frontDesk, Map.of(
                        "signaturePngBase64", signaturePngBase64(),
                        "signedByName", "Signa Ture"));
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signed.getBody().get("status")).isEqualTo("SIGNED");
        assertThat(signed.getBody().get("signedAt")).isNotNull();
        assertThat(signed.getBody().get("signedByName")).isEqualTo("Signa Ture");
        String documentId = (String) signed.getBody().get("documentId");
        assertThat(documentId).isNotNull();

        // the PDF landed in the patient's documents
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) api
                .get("/api/v1/documents?patientId=" + patientId, frontDesk)
                .getBody().get("content");
        assertThat(documents).anyMatch(d -> documentId.equals(d.get("id"))
                && "CONSENT".equals(d.get("category"))
                && "application/pdf".equals(d.get("contentType")));

        // and the stored bytes are a real PDF
        ResponseEntity<byte[]> download = rest.exchange(
                "/api/v1/documents/" + documentId + "/download", HttpMethod.GET,
                new HttpEntity<>(null, frontDesk), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody(), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");

        // signed instances are immutable
        assertThat(putAnswers(id, Map.of("allergies", "Changed", "smoker", "Yes"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // visible in the patient's instance list and by id
        assertThat(api.getList("/api/v1/forms/instances?patientId=" + patientId, readOnly)
                .getBody()).anyMatch(i -> id.equals(i.get("id")));
        assertThat(api.get("/api/v1/forms/instances/" + id, readOnly)
                .getBody().get("status")).isEqualTo("SIGNED");
    }

    @Test
    void signingRequiresCompletedStatus() {
        String id = createInstance();
        ResponseEntity<Map<String, Object>> response = api.post(
                "/api/v1/forms/instances/" + id + "/sign", frontDesk, Map.of(
                        "signaturePngBase64", signaturePngBase64(),
                        "signedByName", "Too Early"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void badSignaturePayloadsAreRejected() {
        String id = createInstance();
        putAnswers(id, Map.of("allergies", "None", "smoker", "No"));

        // not base64 at all
        assertThat(api.post("/api/v1/forms/instances/" + id + "/sign", frontDesk, Map.of(
                "signaturePngBase64", "not-base64!!!", "signedByName", "X"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // decodes fine but is not a PNG
        String notPng = Base64.getEncoder()
                .encodeToString("just some text".getBytes(StandardCharsets.UTF_8));
        assertThat(api.post("/api/v1/forms/instances/" + id + "/sign", frontDesk, Map.of(
                "signaturePngBase64", notPng, "signedByName", "X"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rbacIsEnforced() {
        String id = createInstance();
        putAnswers(id, Map.of("allergies", "None", "smoker", "No"));

        assertThat(api.post("/api/v1/forms/instances", readOnly,
                Map.of("patientId", patientId, "templateId", templateId)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.put("/api/v1/forms/instances/" + id + "/answers", readOnly,
                Map.of("answers", Map.of("allergies", "X"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/forms/instances/" + id + "/sign", readOnly, Map.of(
                "signaturePngBase64", signaturePngBase64(), "signedByName", "X"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // reads stay open to any authenticated user
        assertThat(api.get("/api/v1/forms/instances/" + id, readOnly).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
