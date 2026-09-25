package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimePropertiesTest {
    @Test
    void acceptsSecureSeparatedProductionConfiguration() {
        RuntimeProperties properties = validProperties();
        properties.setDatabaseUrl("jdbc:postgresql://db.internal:5432/whatplan?sslmode=require");
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void rejectsCredentialsEmbeddedInAuthenticationUrl() {
        RuntimeProperties properties = validProperties();
        properties.setAuthServiceUrl("https://user:password@auth.internal");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void rejectsDatabaseUrlWithEmbeddedPassword() {
        RuntimeProperties properties = validProperties();
        properties.setDatabaseUrl("jdbc:postgresql://runtime:password@db.internal:5432/whatplan");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void rejectsSharedOrWeakSecretsAndInsecureCookieMode() {
        RuntimeProperties properties = validProperties();
        properties.setDatabaseMigrationPassword(properties.getDatabaseRuntimePassword());
        assertThrows(IllegalStateException.class, properties::validate);

        properties = validProperties();
        properties.setRedisPassword("replace-with-a-short-placeholder");
        assertThrows(IllegalStateException.class, properties::validate);

        properties = validProperties();
        properties.setAuthCookieSecure(false);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    private static RuntimeProperties validProperties() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.setAuthServiceUrl("http://auth-service:8080");
        properties.setDatabaseUrl("jdbc:postgresql://postgres:5432/whatplan");
        properties.setDatabaseAdminUser("postgres-admin");
        properties.setDatabaseRuntimeUser("whatplan_runtime");
        properties.setDatabaseRuntimePassword("runtime-secret-32-characters-long");
        properties.setDatabaseMigrationUser("whatplan_migrator");
        properties.setDatabaseMigrationPassword("migration-secret-32-characters-long");
        properties.setRedisPassword("redis-secret-32-characters-long");
        properties.setAuthCookieSecure(true);
        return properties;
    }
}
