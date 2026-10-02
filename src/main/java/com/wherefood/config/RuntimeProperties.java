package com.wherefood.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.runtime")
public class RuntimeProperties {
    private String authServiceUrl;
    private String databaseUrl;
    private String databaseAdminUser;
    private String databaseRuntimeUser;
    private String databaseRuntimePassword;
    private String databaseMigrationUser;
    private String databaseMigrationPassword;
    private String redisPassword;
    private String trustedProxyAddresses;
    private boolean authCookieSecure = true;

    public void validate() {
        validateAuthUrl(authServiceUrl);
        validateDatabaseUrl(databaseUrl);
        requireDistinct("DATABASE_RUNTIME_USER", databaseRuntimeUser, "POSTGRES_USER", databaseAdminUser);
        requireDistinct("DATABASE_RUNTIME_USER", databaseRuntimeUser, "DATABASE_MIGRATION_USER", databaseMigrationUser);
        requireDistinct("DATABASE_MIGRATION_USER", databaseMigrationUser, "POSTGRES_USER", databaseAdminUser);
        requireSecret("DATABASE_RUNTIME_PASSWORD", databaseRuntimePassword, 32);
        requireSecret("DATABASE_MIGRATION_PASSWORD", databaseMigrationPassword, 32);
        requireSecret("REDIS_PASSWORD", redisPassword, 32);
        validateTrustedProxyAddresses(trustedProxyAddresses);
        if (databaseRuntimePassword.equals(databaseMigrationPassword)) {
            throw new IllegalStateException("DATABASE_RUNTIME_PASSWORD and DATABASE_MIGRATION_PASSWORD must differ");
        }
        if (!authCookieSecure) {
            throw new IllegalStateException("AUTH_COOKIE_SECURE must be true for WhatPlan sessions");
        }
    }

    private static void validateTrustedProxyAddresses(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("TRUSTED_PROXY_ADDRESSES must contain at least one exact proxy IP address");
        }
        for (String address : value.split(",", -1)) {
            if (address.isBlank() || IpAddress.canonicalize(address.trim()) == null) {
                throw new IllegalStateException("TRUSTED_PROXY_ADDRESSES must contain only exact IPv4/IPv6 addresses, not hostnames or CIDRs");
            }
        }
    }

    private static void validateAuthUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException("AUTH_SERVICE_URL must be an exact HTTP(S) origin without credentials or path");
            }
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IllegalStateException("AUTH_SERVICE_URL is invalid", exception);
        }
    }

    private static void validateDatabaseUrl(String value) {
        if (value == null || !value.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DATABASE_URL must be a PostgreSQL JDBC URL");
        }
        try {
            URI uri = new URI(value.substring("jdbc:".length()));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPath() == null || uri.getPath().length() < 2) {
                throw new IllegalStateException("DATABASE_URL must identify a database without embedded credentials");
            }
            if (uri.getQuery() != null && uri.getQuery().matches("(?i).*(^|&)(user|password)=.*")) {
                throw new IllegalStateException("DATABASE_URL must not embed database credentials");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("DATABASE_URL is invalid", exception);
        }
    }

    private static void requireDistinct(String firstName, String firstValue, String secondName, String secondValue) {
        if (firstValue == null || secondValue == null || firstValue.isBlank() || secondValue.isBlank()
                || firstValue.equalsIgnoreCase(secondValue)) {
            throw new IllegalStateException(firstName + " and " + secondName + " must be set and distinct");
        }
    }

    private static void requireSecret(String name, String value, int minimumLength) {
        String normalized = value == null ? "" : value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() < minimumLength || lower.contains("replace-") || lower.contains("change-me")
                || lower.equals("password") || lower.equals("secret")) {
            throw new IllegalStateException(name + " must be a non-placeholder secret of at least " + minimumLength + " characters");
        }
    }

    public String getAuthServiceUrl() { return authServiceUrl; }
    public void setAuthServiceUrl(String authServiceUrl) { this.authServiceUrl = authServiceUrl; }
    public String getDatabaseUrl() { return databaseUrl; }
    public void setDatabaseUrl(String databaseUrl) { this.databaseUrl = databaseUrl; }
    public String getDatabaseAdminUser() { return databaseAdminUser; }
    public void setDatabaseAdminUser(String databaseAdminUser) { this.databaseAdminUser = databaseAdminUser; }
    public String getDatabaseRuntimeUser() { return databaseRuntimeUser; }
    public void setDatabaseRuntimeUser(String databaseRuntimeUser) { this.databaseRuntimeUser = databaseRuntimeUser; }
    public String getDatabaseRuntimePassword() { return databaseRuntimePassword; }
    public void setDatabaseRuntimePassword(String databaseRuntimePassword) { this.databaseRuntimePassword = databaseRuntimePassword; }
    public String getDatabaseMigrationUser() { return databaseMigrationUser; }
    public void setDatabaseMigrationUser(String databaseMigrationUser) { this.databaseMigrationUser = databaseMigrationUser; }
    public String getDatabaseMigrationPassword() { return databaseMigrationPassword; }
    public void setDatabaseMigrationPassword(String databaseMigrationPassword) { this.databaseMigrationPassword = databaseMigrationPassword; }
    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }
    public String getTrustedProxyAddresses() { return trustedProxyAddresses; }
    public void setTrustedProxyAddresses(String trustedProxyAddresses) { this.trustedProxyAddresses = trustedProxyAddresses; }
    public boolean isAuthCookieSecure() { return authCookieSecure; }
    public void setAuthCookieSecure(boolean authCookieSecure) { this.authCookieSecure = authCookieSecure; }
}
