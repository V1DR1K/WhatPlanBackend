package com.wherefood.config;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** A sanitized API failure that tells clients when to retry. */
public final class RetryAfterResponseStatusException extends ResponseStatusException {
    private final long retryAfterSeconds;

    public RetryAfterResponseStatusException(HttpStatusCode status, String reason, long retryAfterSeconds) {
        super(status, reason);
        if (retryAfterSeconds < 1) throw new IllegalArgumentException("Retry-After must be positive");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
