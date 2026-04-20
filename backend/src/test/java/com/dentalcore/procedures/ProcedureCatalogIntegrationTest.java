package com.dentalcore.procedures;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProcedureCatalogIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin-catalog@clinic.test";
    private static final String HYGIENIST_EMAIL = "hygienist-catalog@clinic.test";
    private static final String PASSWORD = "integration-pass-1";
    private static final AtomicInteger CODE_SEQ = new AtomicInteger(9000);

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
    private HttpHeaders hygienist;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(rest);
        seedUser(ADMIN_EMAIL, "ADMIN");
        seedUser(HYGIENIST_EMAIL, "HYGIENIST");
        admin = api.login(ADMIN_EMAIL, PASSWORD);
        hygienist = api.login(HYGIENIST_EMAIL, PASSWORD);
    }

    private void seedUser(String email, String role) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test", role, null);
        user.setRoles(Set.of(roleRepository.findByName(role).orElseThrow()));
        userRepository.save(user);
    }

    private Map<String, Object> codeBody(String code) {
        return Map.of("code", code, "description", "Test procedure " + code,
                "category", "OTHER", "standardFee", 99.50);
    }

    @Test
    void seededCdtCodesAreSearchable() {
        ResponseEntity<Map<String, Object>> result =
                api.get("/api/v1/procedure-codes?q=prophylaxis", hygienist);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getBody().get("content");
        assertThat(content).extracting(c -> c.get("code")).contains("D1110", "D1120");
    }

    @Test
    void adminCanCreateAndUpdateCatalogEntries() {
        String code = "T" + CODE_SEQ.incrementAndGet();
        ResponseEntity<Map<String, Object>> created =
                api.post("/api/v1/procedure-codes", admin, codeBody(code));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("standardFee")).isEqualTo(99.5);

        String id = (String) created.getBody().get("id");
        Map<String, Object> update = Map.of("code", code, "description", "Updated",
                "category", "PREVENTIVE", "standardFee", 150.00, "active", false);
        ResponseEntity<Map<String, Object>> updated =
                api.put("/api/v1/procedure-codes/" + id, admin, update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("active")).isEqualTo(false);
    }

    @Test
    void duplicateCodeIsRejected() {
        String code = "T" + CODE_SEQ.incrementAndGet();
        api.post("/api/v1/procedure-codes", admin, codeBody(code));
        assertThat(api.post("/api/v1/procedure-codes", admin, codeBody(code)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nonAdminCannotModifyCatalog() {
        assertThat(api.post("/api/v1/procedure-codes", hygienist,
                codeBody("T" + CODE_SEQ.incrementAndGet())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void negativeFeeFailsValidation() {
        Map<String, Object> bad = Map.of("code", "T" + CODE_SEQ.incrementAndGet(),
                "description", "Bad", "category", "OTHER", "standardFee", -5);
        assertThat(api.post("/api/v1/procedure-codes", admin, bad).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
