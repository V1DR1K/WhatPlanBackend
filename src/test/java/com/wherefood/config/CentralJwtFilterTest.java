package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Users;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class CentralJwtFilterTest {
    private final CentralJwt jwt = mock(CentralJwt.class);
    private final Users users = mock(Users.class);
    private final CentralJwtFilter filter = new CentralJwtFilter(jwt, users);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAValidCentralBearerTokenCaseInsensitively() throws Exception {
        UUID authId = UUID.randomUUID();
        User user = new User();
        user.authUserId = authId;
        user.username = "avril";
        user.role = Role.ADMIN;
        when(jwt.subject("valid-token")).thenReturn(authId);
        when(users.findByAuthUserId(authId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bearer valid-token");
        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void leavesTheRequestAnonymousWhenTheTokenCannotBeResolved() throws Exception {
        when(jwt.subject("invalid-token")).thenThrow(new IllegalArgumentException("invalid"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsAValidTokenForAnUnlistedWhatPlanUser() throws Exception {
        UUID authId = UUID.randomUUID();
        User user = new User();
        user.authUserId = authId;
        user.username = "other-user";
        user.role = Role.USER;
        when(jwt.subject("valid-token")).thenReturn(authId);
        when(users.findByAuthUserId(authId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
