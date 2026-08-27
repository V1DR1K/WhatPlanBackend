package com.wherefood.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AllowedWhatPlanUsers {
    private final Set<String> usernames;

    public AllowedWhatPlanUsers(@Value("${app.auth.allowed-users:tomas,avril}") String configuredUsers) {
        this.usernames = Arrays.stream(configuredUsers.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (usernames.isEmpty()) {
            throw new IllegalStateException("app.auth.allowed-users must contain at least one username");
        }
    }

    public boolean isAllowed(String username) {
        return username != null && usernames.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public void requireAllowed(String username) {
        if (!isAllowed(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario no habilitado para WhatPlan");
        }
    }

    public static AllowedWhatPlanUsers defaults() {
        return new AllowedWhatPlanUsers("tomas,avril");
    }
}
