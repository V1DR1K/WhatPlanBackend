package com.wherefood.config;

import com.wherefood.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Shared, fail-closed request limits. Forwarded addresses are only used behind explicitly trusted proxies. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final SharedRateLimiter limiter;
    private final Set<String> trustedProxies;

    public RequestRateLimitFilter(SharedRateLimiter limiter, String trustedProxyAddresses) {
        this.limiter = limiter;
        this.trustedProxies = Arrays.stream(trustedProxyAddresses == null ? new String[0] : trustedProxyAddresses.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(RequestRateLimitFilter::canonicalIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return policy(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policy(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String identity = authenticatedIdentity();
        if (policy.name.equals("login") || identity == null) identity = ip;
        String refreshCookie = null;
        if (policy.name.equals("refresh")) {
            refreshCookie = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                    .filter(value -> "whatplan_refresh".equals(value.getName())).map(value -> value.getValue()).findFirst().orElse(null);
        }

        final boolean allowed;
        try {
            if (policy.name.equals("refresh")) {
                boolean ipAllowed = limiter.allow("refresh-ip", ip, 60, Duration.ofMinutes(5));
                boolean cookieAllowed = refreshCookie == null || refreshCookie.isBlank()
                        || limiter.allow("refresh-cookie", refreshCookie, 10, Duration.ofMinutes(5));
                allowed = ipAllowed && cookieAllowed;
            } else if (policy.name.equals("upload")) {
                boolean userAllowed = limiter.allow("upload-user", identity, policy.limit, policy.window);
                UUID coupleId = CoupleContext.current();
                boolean coupleAllowed = userAllowed && (coupleId == null
                        || limiter.allow("upload-couple", coupleId.toString(), 40, policy.window));
                allowed = userAllowed && coupleAllowed;
            } else {
                allowed = limiter.allow(policy.name, identity, policy.limit, policy.window);
            }
        } catch (RuntimeException unavailable) {
            ProblemDetailsSupport.write(response, HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE",
                    "El control de solicitudes no está disponible temporalmente.", 5L);
            return;
        }
        if (allowed) chain.doFilter(request, response);
        else ProblemDetailsSupport.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Demasiadas solicitudes. Intentá nuevamente más tarde.", policy.window.toSeconds());
    }

    private String clientIp(HttpServletRequest request) {
        String remote = canonicalIp(request.getRemoteAddr());
        if (remote == null || !trustedProxies.contains(remote)) return remote == null ? "unknown" : remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 2048) return remote;
        String[] hops = forwarded.split(",");
        String candidate = remote;
        for (int index = hops.length - 1; index >= 0; index--) {
            String hop = canonicalIp(hops[index].trim());
            if (hop == null) return remote;
            if (trustedProxies.contains(candidate)) candidate = hop;
            else break;
        }
        return candidate;
    }

    private static String canonicalIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return null;
        boolean ipv4 = value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6 = value.indexOf(':') >= 0 && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return null;
        if (ipv4) {
            for (String octet : value.split("\\.")) {
                try {
                    if (Integer.parseInt(octet) > 255) return null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static String authenticatedIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user && user.id != null) {
            return "user:" + user.id;
        }
        return null;
    }

    private static Policy policy(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && "/api/auth/login".equals(path)) return new Policy("login", 10, Duration.ofMinutes(15));
        if ("POST".equals(method) && "/api/auth/register".equals(path)) return new Policy("register", 5, Duration.ofMinutes(30));
        if ("POST".equals(method) && "/api/auth/refresh".equals(path)) return new Policy("refresh", 60, Duration.ofMinutes(5));
        if ("POST".equals(method) && "/api/couple/invitations".equals(path)) return new Policy("invite-create", 20, Duration.ofMinutes(5));
        if ("POST".equals(method) && "/api/couple/invitations/accept".equals(path)) return new Policy("invite-accept", 15, Duration.ofMinutes(5));
        if ("GET".equals(method) && path.startsWith("/api/tmdb/")) return new Policy("tmdb-read", 30, Duration.ofMinutes(1));
        if ("POST".equals(method) && path.startsWith("/api/") && isMultipart(request)) return new Policy("upload", 20, Duration.ofHours(1));
        if ("GET".equals(method) && path.startsWith("/api/") && (path.contains("/photo") || path.contains("/photos"))) {
            return new Policy("media-read", 1200, Duration.ofMinutes(5));
        }
        return null;
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private record Policy(String name, int limit, Duration window) {}
}
