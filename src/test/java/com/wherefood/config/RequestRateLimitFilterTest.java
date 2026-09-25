package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestRateLimitFilterTest {
    private final SharedRateLimiter limiter = mock(SharedRateLimiter.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresForwardedAddressFromUntrustedPeer() throws Exception {
        when(limiter.allow(eq("login"), eq("203.0.113.8"), eq(10), any(Duration.class))).thenReturn(true);
        invoke("203.0.113.8", "1.1.1.1", "10.0.0.2");
        verify(limiter).allow(eq("login"), eq("203.0.113.8"), eq(10), any(Duration.class));
    }

    @Test
    void usesForwardedClientAddressOnlyWhenPeerIsTrusted() throws Exception {
        when(limiter.allow(eq("login"), eq("198.51.100.5"), eq(10), any(Duration.class))).thenReturn(true);
        invoke("10.0.0.2", "198.51.100.5, 10.0.0.2", "10.0.0.2");
        verify(limiter).allow(eq("login"), eq("198.51.100.5"), eq(10), any(Duration.class));
    }

    @Test
    void failsClosedWhenSharedLimiterIsUnavailable() throws Exception {
        when(limiter.allow(eq("login"), any(String.class), eq(10), any(Duration.class)))
                .thenThrow(new IllegalStateException("Redis is unavailable"));
        MockHttpServletResponse response = invoke("203.0.113.8", null, "");
        assertEquals(503, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    private MockHttpServletResponse invoke(String remote, String forwarded, String trusted) throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(limiter, trusted);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remote);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};
        filter.doFilter(request, response, chain);
        return response;
    }
}
