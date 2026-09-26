package com.wherefood.auth;

import com.wherefood.config.RetryAfterResponseStatusException;
import com.wherefood.config.SharedRateLimiter;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Adds a shared account-level budget alongside the request filter's IP-level login limit. */
@Service
public class LoginAttemptProtection {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final long RETRY_AFTER_SECONDS = WINDOW.toSeconds();
    private final SharedRateLimiter limiter;

    public LoginAttemptProtection(SharedRateLimiter limiter) {
        this.limiter = limiter;
    }

    public void checkAccount(String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        final boolean allowed;
        try {
            allowed = limiter.allow("login-account", normalized, 30, WINDOW);
        } catch (RuntimeException unavailable) {
            throw new RetryAfterResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El control de intentos de acceso no está disponible temporalmente.", 5);
        }
        if (!allowed) {
            throw new RetryAfterResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos para esta cuenta. Intentá nuevamente más tarde.", RETRY_AFTER_SECONDS);
        }
    }
}
