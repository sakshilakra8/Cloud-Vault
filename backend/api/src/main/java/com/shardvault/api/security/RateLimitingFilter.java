package com.shardvault.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    private static final Set<String> AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
    );

    public RateLimitingFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String clientIp = extractClientIp(request);

        if (AUTH_ENDPOINTS.contains(path)) {
            var decision = rateLimiterService.tryConsumeAuth(clientIp);
            if (!decision.allowed()) {
                sendRateLimitResponse(request, response, decision.retryAfterSeconds());
                return;
            }
        } else if (path.startsWith("/s/")) {
            var decision = rateLimiterService.tryConsumeApi("share-ip:" + clientIp);
            if (!decision.allowed()) {
                sendRateLimitResponse(request, response, decision.retryAfterSeconds());
                return;
            }
        } else if (path.startsWith("/api/v1/")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String key;
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                key = "user:" + auth.getName();
            } else {
                key = "ip:" + clientIp;
            }

            var decision = rateLimiterService.tryConsumeApi(key);
            if (!decision.allowed()) {
                sendRateLimitResponse(request, response, decision.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendRateLimitResponse(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please retry after " + retryAfterSeconds + " seconds."
        );
        problem.setTitle("Too Many Requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String extractClientIp(HttpServletRequest request) {
        // Forwarded headers are client supplied unless a separately configured trusted proxy rewrites them.
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
