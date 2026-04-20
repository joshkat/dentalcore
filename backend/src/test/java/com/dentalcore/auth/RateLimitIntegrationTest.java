package com.dentalcore.auth;

import com.dentalcore.support.ApiTestClient;
import com.dentalcore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "dentalcore.security.rate-limit.max-requests=3")
class RateLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginIsRateLimitedPerIpButOtherEndpointsAreNot() {
        ApiTestClient api = new ApiTestClient(rest);
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> badCreds = Map.of(
                "email", "rate-limit@clinic.test", "password", "whatever-pass-1");

        // first 3 attempts pass the limiter (and fail auth normally)
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map<String, Object>> response =
                    api.exchange("/api/v1/auth/login", HttpMethod.POST, json, badCreds);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 4th within the window is throttled
        ResponseEntity<Map<String, Object>> throttled =
                api.exchange("/api/v1/auth/login", HttpMethod.POST, json, badCreds);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat((String) throttled.getBody().get("detail")).contains("Too many attempts");

        // unauthenticated non-auth endpoints are untouched by the limiter
        ResponseEntity<Map<String, Object>> other =
                api.exchange("/api/v1/patients", HttpMethod.GET, json, null);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
