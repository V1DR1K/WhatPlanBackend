package com.wherefood.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.RetryAfterResponseStatusException;
import com.wherefood.config.SharedRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginAttemptProtectionTest {
    private final SharedRateLimiter limiter = mock(SharedRateLimiter.class);
    private final LoginAttemptProtection protection = new LoginAttemptProtection(limiter);

    @Test
    void normalizesAccountIdentityBeforeCheckingSharedBudget() {
        when(limiter.allow("login-account", "alice@example.com", 30, Duration.ofMinutes(15))).thenReturn(true);

        protection.checkAccount("  Alice@Example.com ");

        verify(limiter).allow("login-account", "alice@example.com", 30, Duration.ofMinutes(15));
    }

    @Test
    void rejectsExceededAccountBudgetWithRetryAfter() {
        when(limiter.allow("login-account", "alice", 30, Duration.ofMinutes(15))).thenReturn(false);

        RetryAfterResponseStatusException error = assertThrows(RetryAfterResponseStatusException.class,
                () -> protection.checkAccount("Alice"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals(900L, error.retryAfterSeconds());
    }

    @Test
    void failsClosedWhenSharedRateLimitStoreIsUnavailable() {
        when(limiter.allow("login-account", "alice", 30, Duration.ofMinutes(15)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        RetryAfterResponseStatusException error = assertThrows(RetryAfterResponseStatusException.class,
                () -> protection.checkAccount("Alice"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals(5L, error.retryAfterSeconds());
    }
}
