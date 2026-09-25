package com.wherefood.config;

import com.wherefood.domain.User;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.repo.Repositories.Users;
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

    @org.springframework.beans.factory.annotation.Autowired
    public CentralJwtFilter(CentralJwt jwt, Users users, CoupleAuthorizationService coupleAuthorization) {
        this.jwt = jwt;
        this.users = users;
        this.coupleAuthorization = coupleAuthorization;
    }

    public CentralJwtFilter(CentralJwt jwt, Users users) {
        this(jwt, users, new CoupleAuthorizationService(null));
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CoupleContext.clear();
        SecurityContextHolder.clearContext();
        try {
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
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            CoupleContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
