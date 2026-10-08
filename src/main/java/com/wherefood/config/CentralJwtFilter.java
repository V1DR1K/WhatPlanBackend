package com.wherefood.config;

import com.wherefood.domain.User;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.repo.Repositories.Users;
import com.wherefood.repo.Repositories.Couples;
import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CentralJwtFilter extends OncePerRequestFilter {
    private final CentralJwt jwt;
    private final Users users;
    private final CoupleAuthorizationService coupleAuthorization;
    private final Couples couples;

    @org.springframework.beans.factory.annotation.Autowired
    public CentralJwtFilter(CentralJwt jwt, Users users, CoupleAuthorizationService coupleAuthorization, Couples couples) {
        this.jwt = jwt;
        this.users = users;
        this.coupleAuthorization = coupleAuthorization;
        this.couples = couples;
    }

    public CentralJwtFilter(CentralJwt jwt, Users users, CoupleAuthorizationService coupleAuthorization) {
        this(jwt, users, coupleAuthorization, null);
    }

    public CentralJwtFilter(CentralJwt jwt, Users users) {
        this(jwt, users, new CoupleAuthorizationService(null), null);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CoupleContext.clear();
        AdminCoupleContext.clear();
        SecurityContextHolder.clearContext();
        try {
            if (request.getServletPath().startsWith("/api/auth/")) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
            }
            String header = request.getHeader("Authorization");
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                UUID subject = null;
                try {
                    String token = header.substring(7).trim();
                    if (token.isBlank() || token.length() > 8192) throw new IllegalArgumentException("Bearer token length is invalid");
                    subject = jwt.subject(token);
                } catch (JwtException | IllegalArgumentException ignored) {
                    SecurityContextHolder.clearContext();
                    CoupleContext.clear();
                }
                // Keep database and tenant-resolution failures outside the invalid-token catch.
                // A backend outage must not be misreported as an anonymous/invalid session.
                if (subject != null) {
                    User user = users.findByAuthUserId(subject).orElse(null);
                    if (user != null) {
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name()))));
                        coupleAuthorization.resolvePrivateCouple(user).ifPresent(CoupleContext::set);
                        if (!applyAdminCoupleScope(request, response, user)) return;
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            AdminCoupleContext.clear();
            CoupleContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean applyAdminCoupleScope(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        String requested = request.getHeader("X-WhatPlan-Admin-Couple");
        if (requested == null || requested.isBlank() || !isPrivateWorkspacePath(request.getServletPath())) return true;
        if (user.role != com.wherefood.domain.Role.ADMIN) {
            ProblemDetailsSupport.write(response, org.springframework.http.HttpStatus.FORBIDDEN,
                    "FORBIDDEN", "La solicitud no está permitida.", null);
            return false;
        }
        if (requested.length() > 36) {
            ProblemDetailsSupport.write(response, org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_COUPLE_SCOPE", "El identificador de pareja no es válido.", null);
            return false;
        }
        java.util.UUID coupleId;
        try {
            coupleId = java.util.UUID.fromString(requested);
        } catch (IllegalArgumentException invalidId) {
            ProblemDetailsSupport.write(response, org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_COUPLE_SCOPE", "El identificador de pareja no es válido.", null);
            return false;
        }
        if (couples == null) return true;
        Couple couple = couples.findById(coupleId).orElse(null);
        if (couple == null) {
            ProblemDetailsSupport.write(response, org.springframework.http.HttpStatus.NOT_FOUND,
                    "COUPLE_NOT_FOUND", "No encontramos esa pareja.", null);
            return false;
        }
        String method = request.getMethod();
        boolean readOnlyRequest = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
        if (couple.status == CoupleStatus.CLOSED && !readOnlyRequest) {
            ProblemDetailsSupport.write(response, org.springframework.http.HttpStatus.CONFLICT,
                    "COUPLE_CLOSED", "La pareja está cerrada; reabrila desde administración para editar su contenido.", null);
            return false;
        }
        AdminCoupleContext.set(coupleId);
        CoupleContext.set(coupleId);
        return true;
    }

    private static boolean isPrivateWorkspacePath(String path) {
        return path.startsWith("/api/")
                && !path.startsWith("/api/auth/")
                && !path.startsWith("/api/admin/")
                && !path.equals("/api/couple") && !path.startsWith("/api/couple/")
                && !path.equals("/api/couples") && !path.startsWith("/api/couples/")
                && !path.startsWith("/api/actuator/")
                && !path.startsWith("/api/categories")
                && !path.startsWith("/api/settings")
                && !path.startsWith("/api/zones") && !path.startsWith("/api/locations")
                && !path.startsWith("/api/films/platforms") && !path.startsWith("/api/films/genres")
                && !path.startsWith("/api/why-fun/categories")
                && !path.startsWith("/api/journey/point-types");
    }
}
