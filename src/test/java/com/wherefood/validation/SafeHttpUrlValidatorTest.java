package com.wherefood.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeHttpUrlValidatorTest {
    private final SafeHttpUrlValidator validator = new SafeHttpUrlValidator();

    @Test
    void acceptsHttpAndHttpsUrlsWithoutCredentialsOrFragments() {
        assertTrue(validator.isValid("https://example.test/path?q=1", null));
        assertTrue(validator.isValid("http://localhost:8080", null));
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void rejectsUnsafeOrMalformedUrls() {
        assertFalse(validator.isValid("javascript:alert(1)", null));
        assertFalse(validator.isValid("https://user:password@example.test", null));
        assertFalse(validator.isValid("https://example.test/#fragment", null));
        assertFalse(validator.isValid("https://example.test/with space", null));
    }
}
