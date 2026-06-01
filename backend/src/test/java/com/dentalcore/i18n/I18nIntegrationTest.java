package com.dentalcore.i18n;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend internationalization: public config endpoint, self-service language
 * preferences, and the lang resolution chain for PDFs
 * (?lang param &gt; user export_language &gt; instance default).
 */
class I18nIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-i18n@clinic.test";
    private static final String BILLING_EMAIL = "billing-i18n@clinic.test";
    private static final String BILLING_ES_EMAIL = "billing-es-i18n@clinic.test";
    private static final String FRONT_EMAIL = "front-i18n@clinic.test";
    private static final String DENTIST_EMAIL = "dentist-i18n@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicLong SEQ = new AtomicLong(2_900_000_000L);

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
    private HttpHeaders billing;
    private HttpHeaders frontDesk;
    private HttpHeaders dentist;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(BILLING_EMAIL, "BILLING");
        seedUser(BILLING_ES_EMAIL, "BILLING");
        seedUser(FRONT_EMAIL, "FRONT_DESK");
        seedUser(DENTIST_EMAIL, "DENTIST");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        billing = api.login(BILLING_EMAIL, PASSWORD);
        frontDesk = api.login(FRONT_EMAIL, PASSWORD);
        dentist = api.login(DENTIST_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private String newPatientWithLedger(HttpHeaders chargedBy) {
        String patientId = (String) api.post("/api/v1/patients", admin, Map.of(
                "firstName", "Idioma", "lastName", "Paciente" + SEQ.incrementAndGet(),
                "dateOfBirth", "1985-05-05", "sex", "FEMALE")).getBody().get("id");
        api.post("/api/v1/billing/charges", chargedBy, Map.of(
                "patientId", patientId, "amount", 200, "description", "i18n test charge"));
        return patientId;
    }

    private ResponseEntity<byte[]> statementPdf(String patientId, HttpHeaders as, String lang) {
        LocalDate today = LocalDate.now();
        String url = "/api/v1/billing/statement?patientId=" + patientId
                + "&from=" + today.minusDays(30) + "&to=" + today.plusDays(1)
                + (lang == null ? "" : "&lang=" + lang);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(null, as), byte[].class);
    }

    private static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    // ---- config endpoint ----

    @Test
    void configEndpointIsPublicAndAdvertisesLanguages() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/config", HttpMethod.GET, HttpEntity.EMPTY,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // reflects dentalcore.i18n.default-language (test profile default: en)
        assertThat(response.getBody().get("defaultLanguage")).isEqualTo("en");
        assertThat(response.getBody().get("supportedLanguages"))
                .isEqualTo(List.of("en", "es"));
    }

    // ---- preferences ----

    @Test
    void preferencesRoundTripIncludingNullResetAndEffectiveFallback() {
        // unauthenticated access is rejected
        assertThat(rest.exchange("/api/v1/users/me/preferences", HttpMethod.GET,
                HttpEntity.EMPTY, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // defaults: no preference, effective values fall back to the instance default
        Map<String, Object> initial =
                api.get("/api/v1/users/me/preferences", frontDesk).getBody();
        assertThat(initial.get("uiLanguage")).isNull();
        assertThat(initial.get("exportLanguage")).isNull();
        assertThat(initial.get("effectiveUiLanguage")).isEqualTo("en");
        assertThat(initial.get("effectiveExportLanguage")).isEqualTo("en");

        // set both preferences
        Map<String, Object> updated = api.put("/api/v1/users/me/preferences", frontDesk,
                Map.of("uiLanguage", "es", "exportLanguage", "es")).getBody();
        assertThat(updated.get("uiLanguage")).isEqualTo("es");
        assertThat(updated.get("exportLanguage")).isEqualTo("es");
        assertThat(updated.get("effectiveUiLanguage")).isEqualTo("es");
        assertThat(updated.get("effectiveExportLanguage")).isEqualTo("es");
        assertThat(api.get("/api/v1/users/me/preferences", frontDesk).getBody()
                .get("effectiveUiLanguage")).isEqualTo("es");

        // explicit nulls reset to "inherit instance default"
        HttpHeaders json = new HttpHeaders();
        json.putAll(frontDesk);
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> reset = rest.exchange(
                "/api/v1/users/me/preferences", HttpMethod.PUT,
                new HttpEntity<>("{\"uiLanguage\":null,\"exportLanguage\":null}", json),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reset.getBody().get("uiLanguage")).isNull();
        assertThat(reset.getBody().get("effectiveUiLanguage")).isEqualTo("en");
        assertThat(reset.getBody().get("effectiveExportLanguage")).isEqualTo("en");

        // unsupported values are rejected
        assertThat(api.put("/api/v1/users/me/preferences", frontDesk,
                Map.of("uiLanguage", "fr")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.put("/api/v1/users/me/preferences", frontDesk,
                Map.of("exportLanguage", "EN-US")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- statement PDFs ----

    @Test
    void statementRendersSpanishWithLangParam() throws IOException {
        String patientId = newPatientWithLedger(billing);
        ResponseEntity<byte[]> pdf = statementPdf(patientId, billing, "es");
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);

        String text = pdfText(pdf.getBody());
        assertThat(text).contains("Estado de cuenta");
        assertThat(text).contains("Saldo");
    }

    @Test
    void statementDefaultsToEnglish() throws IOException {
        String patientId = newPatientWithLedger(billing);
        ResponseEntity<byte[]> pdf = statementPdf(patientId, billing, null);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);

        String text = pdfText(pdf.getBody());
        assertThat(text).contains("Patient Statement");
        assertThat(text).contains("Current balance");
        assertThat(text).doesNotContain("Estado de cuenta");
    }

    @Test
    void statementUsesRequestingUsersExportLanguageWithoutParam() throws IOException {
        HttpHeaders billingEs = api.login(BILLING_ES_EMAIL, PASSWORD);
        api.put("/api/v1/users/me/preferences", billingEs,
                Map.of("exportLanguage", "es"));

        String patientId = newPatientWithLedger(billingEs);
        ResponseEntity<byte[]> pdf = statementPdf(patientId, billingEs, null);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdfText(pdf.getBody())).contains("Estado de cuenta");

        // an explicit param outranks the stored preference
        assertThat(pdfText(statementPdf(patientId, billingEs, "en").getBody()))
                .contains("Patient Statement");
    }

    @Test
    void invalidLangParamIsRejected() {
        String patientId = newPatientWithLedger(billing);
        assertThat(statementPdf(patientId, billing, "fr").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.exchange(
                "/api/v1/billing/walkout?appointmentId=" + java.util.UUID.randomUUID()
                        + "&lang=zz",
                HttpMethod.GET, new HttpEntity<>(null, frontDesk), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- walk-out ----

    @Test
    void walkoutRendersSpanishWithLangParam() throws IOException {
        String providerId = (String) api.post("/api/v1/providers", admin, Map.of(
                "type", "DENTIST", "firstName", "Idioma", "lastName", "Dentista",
                "npi", String.valueOf(SEQ.incrementAndGet()))).getBody().get("id");
        String operatoryId = (String) api.post("/api/v1/operatories", admin,
                Map.of("name", "I18n Op " + SEQ.get())).getBody().get("id");
        String patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Visita", "lastName", "Resumen" + SEQ.incrementAndGet(),
                "dateOfBirth", "1990-01-01", "sex", "MALE")).getBody().get("id");

        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .plus(SEQ.incrementAndGet() % 1000 + 4000, ChronoUnit.DAYS);
        String appointmentId = (String) api.post("/api/v1/appointments", frontDesk, Map.of(
                "patientId", patientId, "providerId", providerId,
                "operatoryId", operatoryId,
                "startsAt", start.toString(),
                "endsAt", start.plus(1, ChronoUnit.HOURS).toString()))
                .getBody().get("id");
        String codeId = codeId("D1110");
        api.put("/api/v1/appointments/" + appointmentId + "/procedures", dentist,
                Map.of("procedureCodeIds", List.of(codeId)));
        for (String status : List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED")) {
            api.patch("/api/v1/appointments/" + appointmentId + "/status", dentist,
                    Map.of("status", status));
        }

        ResponseEntity<byte[]> pdf = rest.exchange(
                "/api/v1/billing/walkout?appointmentId=" + appointmentId + "&lang=es",
                HttpMethod.GET, new HttpEntity<>(null, frontDesk), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdfText(pdf.getBody())).contains("Resumen de visita");
    }

    // ---- form signing ----

    @Test
    void signedFormPdfRespectsSpanish() throws IOException {
        String templateId = (String) api.post("/api/v1/forms/templates", admin, Map.of(
                "name", "Consentimiento " + SEQ.incrementAndGet(),
                "description", "i18n consent",
                "fields", List.of(Map.of("key", "alergias", "label", "Alergias conocidas",
                        "type", "TEXT", "required", true)))).getBody().get("id");
        String patientId = (String) api.post("/api/v1/patients", frontDesk, Map.of(
                "firstName", "Firma", "lastName", "Paciente" + SEQ.incrementAndGet(),
                "dateOfBirth", "1979-09-09", "sex", "FEMALE")).getBody().get("id");
        String instanceId = (String) api.post("/api/v1/forms/instances", frontDesk,
                Map.of("patientId", patientId, "templateId", templateId))
                .getBody().get("id");
        api.put("/api/v1/forms/instances/" + instanceId + "/answers", frontDesk,
                Map.of("answers", Map.of("alergias", "Penicilina")));

        ResponseEntity<Map<String, Object>> signed = api.post(
                "/api/v1/forms/instances/" + instanceId + "/sign?lang=es", frontDesk,
                Map.of("signaturePngBase64", signaturePngBase64(),
                        "signedByName", "Firma Paciente"));
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String documentId = (String) signed.getBody().get("documentId");
        assertThat(documentId).isNotNull();

        ResponseEntity<byte[]> download = rest.exchange(
                "/api/v1/documents/" + documentId + "/download", HttpMethod.GET,
                new HttpEntity<>(null, frontDesk), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        String text = pdfText(download.getBody());
        assertThat(text).contains("Firmado por");
        assertThat(text).contains("Paciente");
    }

    private String codeId(String code) {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=" + code, frontDesk);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getBody().get("content");
        return (String) content.get(0).get("id");
    }

    private static String signaturePngBase64() {
        try {
            BufferedImage image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 240, 80);
            g.setColor(Color.BLACK);
            g.drawLine(10, 60, 110, 20);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
