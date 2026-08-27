package com.wherefood.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class SafeHttpUrlValidator implements ConstraintValidator<SafeHttpUrl, String> {
    private static final int MAX_URL_LENGTH = 2048;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        if (value.length() > MAX_URL_LENGTH || value.chars().anyMatch(Character::isWhitespace)) return false;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
