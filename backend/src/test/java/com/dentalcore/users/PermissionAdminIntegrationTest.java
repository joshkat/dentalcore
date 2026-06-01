package com.dentalcore.users;

import com.dentalcore.support.IntegrationTest;
import com.dentalcore.users.internal.entity.User;
import com.dentalcore.users.internal.repository.RoleRepository;
import com.dentalcore.users.internal.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Granular permission matrix: shape of the admin API, grant updates with
 * auditing, the ADMIN lockout guard, and — crucially — that revoking a
 * permission takes effect on the next request WITHOUT re-login (cache
 * invalidation proof).
 */
class PermissionAdminIntegrationTest extends IntegrationTest {

    private static final String ADMIN_EMAIL = "perm-admin@clinic.test";
    private static final String BILLING_EMAIL = "perm-billing@clinic.test";
    private static final String PASSWORD = "integration-pass-1";

    /**
     * Captured from the live matrix in @BeforeEach rather than hardcoded:
     * a frozen snapshot silently drops grants whenever V18 gains a code,
     * and the @AfterEach restore would then break later suites' RBAC pins.
     */
    private List<String> originalBillingCodes;
    private List<String> originalReadOnlyCodes;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        for (String email : new String[]{ADMIN_EMAIL, BILLING_EMAIL}) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
        User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "Pat", "Admin", null);
        admin.setRoles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()));
        userRepository.save(admin);

        User billing = new User(BILLING_EMAIL, passwordEncoder.encode(PASSWORD), "Bill", "Ing", null);
        billing.setRoles(Set.of(roleRepository.findByName("BILLING").orElseThrow()));
        userRepository.save(billing);

        originalBillingCodes = grantsOf("BILLING");
        originalReadOnlyCodes = grantsOf("READ_ONLY");
    }

    @SuppressWarnings("unchecked")
    private List<String> grantsOf(String role) {
        ResponseEntity<Map<String, Object>> matrix =
                exchange("/api/v1/admin/permissions", HttpMethod.GET, token(ADMIN_EMAIL), null);
        Map<String, Object> roles = (Map<String, Object>) matrix.getBody().get("roles");
        return List.copyOf((List<String>) roles.get(role));
    }

    /** Other suites pin per-role 200/403s, so always put the matrix back. */
    @AfterEach
    void restoreSeededGrants() {
        HttpHeaders admin = token(ADMIN_EMAIL);
        putGrants(admin, "BILLING", originalBillingCodes);
        putGrants(admin, "READ_ONLY", originalReadOnlyCodes);
    }

    @Test
    void matrixContainsCatalogAndPerRoleGrants() {
        ResponseEntity<Map<String, Object>> response =
                exchange("/api/v1/admin/permissions", HttpMethod.GET, token(ADMIN_EMAIL), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permissions =
                (List<Map<String, Object>>) response.getBody().get("permissions");
        assertThat(permissions).isNotEmpty();
        assertThat(permissions.get(0)).containsKeys("code", "description", "category");
        assertThat(permissions).extracting(p -> p.get("code"))
                .contains("BILLING_POST", "PERMISSIONS_MANAGE", "PATIENTS_MERGE");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> roles =
                (Map<String, List<String>>) response.getBody().get("roles");
        assertThat(roles).containsKeys(
                "ADMIN", "DENTIST", "HYGIENIST", "FRONT_DESK", "BILLING", "READ_ONLY");
        assertThat(roles.get("ADMIN")).contains("PERMISSIONS_MANAGE", "USERS_MANAGE");
        assertThat(roles.get("BILLING"))
                .contains("BILLING_POST", "BILLING_REVERSE", "STATEMENT_RUNS_MANAGE")
                .doesNotContain("USERS_MANAGE", "PERMISSIONS_MANAGE");
        assertThat(roles.get("READ_ONLY")).doesNotContain("BILLING_POST", "REPORTS_VIEW");
    }

    @Test
    void nonAdminCannotUseMatrixEndpoints() {
        HttpHeaders billing = token(BILLING_EMAIL);

        assertThat(exchange("/api/v1/admin/permissions", HttpMethod.GET, billing, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange("/api/v1/admin/roles/READ_ONLY/permissions", HttpMethod.PUT, billing,
                Map.of("permissionCodes", originalReadOnlyCodes))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updatingGrantsPersistsAndIsAudited() {
        HttpHeaders admin = token(ADMIN_EMAIL);
        List<String> expanded = new java.util.ArrayList<>(originalReadOnlyCodes);
        expanded.add("REPORTS_VIEW");

        ResponseEntity<Map<String, Object>> updated =
                putGrants(admin, "READ_ONLY", expanded);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("role")).isEqualTo("READ_ONLY");
        @SuppressWarnings("unchecked")
        List<String> updatedCodes = (List<String>) updated.getBody().get("permissionCodes");
        assertThat(updatedCodes).contains("REPORTS_VIEW");

        ResponseEntity<Map<String, Object>> matrix =
                exchange("/api/v1/admin/permissions", HttpMethod.GET, admin, null);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> roles =
                (Map<String, List<String>>) matrix.getBody().get("roles");
        assertThat(roles.get("READ_ONLY")).contains("REPORTS_VIEW");

        ResponseEntity<Map<String, Object>> audit = exchange(
                "/api/v1/audit-logs?entityType=RolePermissions", HttpMethod.GET, admin, null);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) audit.getBody().get("content");
        assertThat(entries).isNotEmpty();
        Map<String, Object> latest = entries.get(0);
        assertThat(latest.get("action")).isEqualTo("UPDATE");
        assertThat(String.valueOf(latest.get("newValue"))).contains("REPORTS_VIEW");
        assertThat(String.valueOf(latest.get("previousValue"))).contains("PATIENTS_READ");
    }

    @Test
    void revokingBillingPostAppliesWithoutReloginAndRestoringFixesIt() {
        HttpHeaders admin = token(ADMIN_EMAIL);
        // Log the BILLING user in ONCE; the same token is used throughout.
        HttpHeaders billing = token(BILLING_EMAIL);
        Map<String, Object> charge = Map.of(
                "patientId", UUID.randomUUID().toString(),
                "amount", "25.00",
                "description", "permission matrix probe");

        // With the seeded grants the gate passes (400 "Unknown patient" from
        // the service: the random patient does not exist — authorization
        // succeeded and the request reached business logic).
        assertThat(exchange("/api/v1/billing/charges", HttpMethod.POST, billing, charge)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Revoke BILLING_POST from the BILLING role.
        List<String> withoutPost = originalBillingCodes.stream()
                .filter(code -> !code.equals("BILLING_POST")).toList();
        assertThat(putGrants(admin, "BILLING", withoutPost).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // The very next request with the SAME token is rejected.
        assertThat(exchange("/api/v1/billing/charges", HttpMethod.POST, billing, charge)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Restoring the grant fixes it immediately, still without re-login.
        assertThat(putGrants(admin, "BILLING", originalBillingCodes).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/v1/billing/charges", HttpMethod.POST, billing, charge)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminLockoutGuardRejectsRevokingCriticalGrants() {
        HttpHeaders admin = token(ADMIN_EMAIL);

        ResponseEntity<Map<String, Object>> withoutPermissionsManage =
                putGrants(admin, "ADMIN", List.of("USERS_MANAGE", "AUDIT_VIEW"));
        assertThat(withoutPermissionsManage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> withoutUsersManage =
                putGrants(admin, "ADMIN", List.of("PERMISSIONS_MANAGE", "AUDIT_VIEW"));
        assertThat(withoutUsersManage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // The guard refused before changing anything: admin still works.
        assertThat(exchange("/api/v1/admin/permissions", HttpMethod.GET, admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownPermissionCodeIsRejected() {
        ResponseEntity<Map<String, Object>> response = putGrants(
                token(ADMIN_EMAIL), "READ_ONLY", List.of("PATIENTS_READ", "NOT_A_REAL_CODE"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownRoleIsNotFound() {
        ResponseEntity<Map<String, Object>> response = putGrants(
                token(ADMIN_EMAIL), "SUPERUSER", List.of("PATIENTS_READ"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> putGrants(HttpHeaders headers, String role,
                                                          List<String> codes) {
        return exchange("/api/v1/admin/roles/" + role + "/permissions", HttpMethod.PUT,
                headers, Map.of("permissionCodes", codes));
    }

    private HttpHeaders token(String email) {
        ResponseEntity<Map<String, Object>> login = exchange(
                "/api/v1/auth/login", HttpMethod.POST, json(),
                Map.of("email", email, "password", PASSWORD));
        HttpHeaders headers = json();
        headers.setBearerAuth((String) login.getBody().get("accessToken"));
        return headers;
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method,
                                                         HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
