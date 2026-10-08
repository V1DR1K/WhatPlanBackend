package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.UUID;
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
        CoupleContext.clear();
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
        assertEquals("application/problem+json", response.getContentType());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getHeader("X-Request-Id"));
    }

    @Test
    void returnsProblemDetailsAndRetryAfterWhenLimitIsExceeded() throws Exception {
        when(limiter.allow(eq("login"), eq("203.0.113.8"), eq(10), any(Duration.class))).thenReturn(false);
        MockHttpServletResponse response = invoke("203.0.113.8", null, "");
        assertEquals(429, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals("900", response.getHeader("Retry-After"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("\"errorCode\":\"RATE_LIMITED\""));
    }

    @Test
    void limitsPublicRegistrationByClientAddress() throws Exception {
        when(limiter.allow(eq("register"), eq("203.0.113.8"), eq(5), any(Duration.class))).thenReturn(false);
        RequestRateLimitFilter filter = new RequestRateLimitFilter(limiter, "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(429, response.getStatus());
        verify(limiter).allow(eq("register"), eq("203.0.113.8"), eq(5), eq(Duration.ofMinutes(30)));
    }

    @Test
    void appliesBothIndividualAndSharedCoupleUploadLimits() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        CoupleContext.set(coupleId);
        when(limiter.allow(eq("upload-user"), eq("203.0.113.8"), eq(20), any(Duration.class))).thenReturn(true);
        when(limiter.allow(eq("upload-couple"), eq(coupleId.toString()), eq(40), any(Duration.class))).thenReturn(true);

        MockHttpServletResponse response = invokeUpload("203.0.113.8");

        assertEquals(200, response.getStatus());
        verify(limiter).allow(eq("upload-user"), eq("203.0.113.8"), eq(20), any(Duration.class));
        verify(limiter).allow(eq("upload-couple"), eq(coupleId.toString()), eq(40), any(Duration.class));
    }

    @Test
    void rejectsUploadWhenSharedCoupleLimitIsExceeded() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        CoupleContext.set(coupleId);
        when(limiter.allow(eq("upload-user"), eq("203.0.113.8"), eq(20), any(Duration.class))).thenReturn(true);
        when(limiter.allow(eq("upload-couple"), eq(coupleId.toString()), eq(40), any(Duration.class))).thenReturn(false);

        MockHttpServletResponse response = invokeUpload("203.0.113.8");

        assertEquals(429, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("\"errorCode\":\"RATE_LIMITED\""));
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

    private MockHttpServletResponse invokeUpload(String remote) throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(limiter, "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/places/1/photo");
        request.setRemoteAddr(remote);
        request.setContentType("multipart/form-data; boundary=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
        filter.doFilter(request, response, chain);
        return response;
    }
}
