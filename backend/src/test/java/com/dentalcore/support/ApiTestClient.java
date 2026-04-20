package com.dentalcore.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/** Small helper for authenticated JSON calls in integration tests. */
public final class ApiTestClient {

    private final TestRestTemplate rest;

    public ApiTestClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public HttpHeaders login(String email, String password) {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/api/v1/auth/login", HttpMethod.POST, json(),
                Map.of("email", email, "password", password));
        HttpHeaders headers = json();
        headers.setBearerAuth((String) response.getBody().get("accessToken"));
        return headers;
    }

    public ResponseEntity<Map<String, Object>> get(String path, HttpHeaders headers) {
        return exchange(path, HttpMethod.GET, headers, null);
    }

    public ResponseEntity<java.util.List<Map<String, Object>>> getList(String path,
                                                                       HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    public ResponseEntity<Map<String, Object>> post(String path, HttpHeaders headers, Object body) {
        return exchange(path, HttpMethod.POST, headers, body);
    }

    public ResponseEntity<Map<String, Object>> put(String path, HttpHeaders headers, Object body) {
        return exchange(path, HttpMethod.PUT, headers, body);
    }

    public ResponseEntity<Map<String, Object>> patch(String path, HttpHeaders headers, Object body) {
        return exchange(path, HttpMethod.PATCH, headers, body);
    }

    public ResponseEntity<Map<String, Object>> delete(String path, HttpHeaders headers) {
        return exchange(path, HttpMethod.DELETE, headers, null);
    }

    public ResponseEntity<Map<String, Object>> exchange(String path, HttpMethod method,
                                                        HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    public HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
