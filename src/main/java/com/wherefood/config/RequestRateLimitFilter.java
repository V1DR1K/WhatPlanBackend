package com.wherefood.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Small per-instance guard for credential and invitation endpoints. Production should complement it at the edge. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 30;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/login")
                && !path.equals("/api/auth/refresh")
                && !path.equals("/api/couple/invitations")
                && !path.matches("/api/couple/invitations/[^/]+/accept");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + normalizedPath(request.getRequestURI());
        Bucket bucket = buckets.compute(key, (ignored, current) -> current == null || current.expired() ? new Bucket() : current.next());
        if (bucket.count > MAX_ATTEMPTS) {
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"RATE_LIMITED\",\"status\":429,\"detail\":\"Demasiadas solicitudes. Intentá nuevamente más tarde.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String normalizedPath(String path) {
        return path.matches("/api/couple/invitations/[^/]+/accept") ? "/api/couple/invitations/*/accept" : path;
    }

    private static final class Bucket {
        private final Instant startedAt;
        private final int count;

        private Bucket() { this(Instant.now(), 1); }
        private Bucket(Instant startedAt, int count) { this.startedAt = startedAt; this.count = count; }
        private boolean expired() { return startedAt.plus(WINDOW).isBefore(Instant.now()); }
        private Bucket next() { return new Bucket(startedAt, count + 1); }
    }
}
