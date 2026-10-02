package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
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
        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) ->
                assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
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
    void authenticatesAValidTokenForAnyCentralUser() throws Exception {
        UUID authId = UUID.randomUUID();
        User user = new User();
        user.authUserId = authId;
        user.username = "other-user";
        user.role = Role.USER;
        when(jwt.subject("valid-token")).thenReturn(authId);
        when(users.findByAuthUserId(authId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) ->
                assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
    }

    @Test
    void adminAuthenticationDoesNotResolveOrReceivePrivateCoupleContext() throws Exception {
        UUID authId = UUID.randomUUID();
        User admin = new User();
        admin.id = 21L;
        admin.authUserId = authId;
        admin.role = Role.ADMIN;
        CoupleMembers members = mock(CoupleMembers.class);
        CentralJwtFilter adminFilter = new CentralJwtFilter(jwt, users, new CoupleAuthorizationService(members));
        when(jwt.subject("admin-token")).thenReturn(authId);
        when(users.findByAuthUserId(authId)).thenReturn(Optional.of(admin));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer admin-token");
        adminFilter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {
            assertNull(CoupleContext.current());
        });

        verify(members, never()).findActiveCoupleIdByUserId(admin.id);
    }

    @Test
    void doesNotMisreportDatabaseOutageAsInvalidBearerToken() throws Exception {
        UUID authId = UUID.randomUUID();
        when(jwt.subject("valid-token")).thenReturn(authId);
        doThrow(new IllegalStateException("database unavailable")).when(users).findByAuthUserId(authId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class)));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(CoupleContext.current());
    }

    @Test
    void marksAuthEndpointResponsesAsNonCacheable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/refresh");
        request.setServletPath("/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
    }
}
