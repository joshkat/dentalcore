package com.dentalcore.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window per-IP rate limit on credential-guessing surfaces
 * (login, password reset). Account lockout protects individual accounts;
 * this slows attackers rotating across many accounts from one address.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Boolean> LIMITED_PATHS = Map.of(
            "/api/v1/auth/login", true,
            "/api/v1/auth/forgot-password", true);

    private final int maxRequests;
    private final long windowMillis;
    private final boolean trustProxyHeaders;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${dentalcore.security.rate-limit.max-requests:15}") int maxRequests,
            @Value("${dentalcore.security.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${dentalcore.security.rate-limit.trust-proxy-headers:false}")
            boolean trustProxyHeaders) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
        this.trustProxyHeaders = trustProxyHeaders;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !LIMITED_PATHS.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = clientIp(request);
        if (!tryAcquire(clientIp)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,\
                    "detail":"Too many attempts. Try again in a minute."}""");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * X-Forwarded-For is client-controlled, so it is only honored when the
     * deployment explicitly declares a trusted reverse proxy in front of the
     * backend; otherwise an attacker could rotate the header to dodge the limit.
     */
    private String clientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
