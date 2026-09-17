package com.wherefood.config;

import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Users;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CentralJwtFilter extends OncePerRequestFilter {
    private final CentralJwt jwt;
    private final Users users;
    private final CoupleMembers coupleMembers;

    @org.springframework.beans.factory.annotation.Autowired
    public CentralJwtFilter(CentralJwt jwt, Users users, CoupleMembers coupleMembers) {
        this.jwt = jwt;
        this.users = users;
        this.coupleMembers = coupleMembers;
    }

    public CentralJwtFilter(CentralJwt jwt, Users users) {
        this(jwt, users, null);
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
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                String token = header.substring(7).trim();
                if (token.isBlank()) throw new IllegalArgumentException("Bearer token is empty");
                User user = users.findByAuthUserId(jwt.subject(token)).orElseThrow();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name()))));
                if (coupleMembers != null) {
                    coupleMembers.findActiveCoupleIdByUserId(user.id).ifPresent(CoupleContext::set);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
                CoupleContext.clear();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CoupleContext.clear();
        }
    }
}
