package com.dentalcore.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private static final int MAX_REQUESTS = 3;

    private MockHttpServletRequest loginRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private int statusOf(AuthRateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void untrustedModeIgnoresForgedForwardedForHeaders() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(MAX_REQUESTS, 60, false);

        // attacker rotates X-Forwarded-For from a single real address
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(statusOf(filter, loginRequest("10.0.0.7", "1.2.3." + i))).isEqualTo(200);
        }
        assertThat(statusOf(filter, loginRequest("10.0.0.7", "9.9.9.9"))).isEqualTo(429);

        // a different real address still gets its own budget
        assertThat(statusOf(filter, loginRequest("10.0.0.8", null))).isEqualTo(200);
    }

    @Test
    void trustedModeKeysOnTheForwardedClientAddress() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(MAX_REQUESTS, 60, true);

        // all requests arrive from the proxy address but represent one client
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(statusOf(filter, loginRequest("172.18.0.2", "203.0.113.5")))
                    .isEqualTo(200);
        }
        assertThat(statusOf(filter, loginRequest("172.18.0.2", "203.0.113.5"))).isEqualTo(429);

        // another forwarded client through the same proxy is not throttled
        assertThat(statusOf(filter, loginRequest("172.18.0.2", "203.0.113.6"))).isEqualTo(200);

        // no header at all falls back to the remote address
        assertThat(statusOf(filter, loginRequest("172.18.0.9", null))).isEqualTo(200);
    }
}
