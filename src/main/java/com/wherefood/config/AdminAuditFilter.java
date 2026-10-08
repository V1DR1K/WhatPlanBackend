package com.wherefood.config;

import com.wherefood.domain.User;
import com.wherefood.web.AdminAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records authenticated API mutations without retaining query strings, bodies or credentials. */
public class AdminAuditFilter extends OncePerRequestFilter {
    private static final Pattern UUID_SEGMENT = Pattern.compile("(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");
    private final AdminAuditService audit;

    public AdminAuditFilter(AdminAuditService audit) {
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            String path = request.getServletPath();
            String method = request.getMethod().toUpperCase(Locale.ROOT);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (path.startsWith("/api/") && !path.startsWith("/api/auth/")
                    && !path.startsWith("/api/actuator/") && !path.startsWith("/api/admin/")
                    && !method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")
                    && authentication != null && authentication.getPrincipal() instanceof User actor) {
                try {
                    audit.recordCurrent(actor, "API_MUTATION", method, normalizePath(path), response.getStatus());
                } catch (RuntimeException ignored) {
                    // Auditing must not replace the endpoint's response when storage is unavailable.
                }
            }
        }
    }

    private static String normalizePath(String path) {
        String normalized = UUID_SEGMENT.matcher(path).replaceAll("{id}");
        normalized = NUMERIC_SEGMENT.matcher(normalized).replaceAll("{id}");
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }
}
