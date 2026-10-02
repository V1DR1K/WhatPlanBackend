package com.wherefood.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/** Prevents the web application from serving requests through a PostgreSQL role that can bypass RLS. */
@Component
@DependsOnDatabaseInitialization
public class DatabaseRuntimeRoleGuard implements InitializingBean {
    private static final String SAFE_RUNTIME_ROLE_QUERY = """
            select not r.rolsuper
                   and not r.rolbypassrls
                   and row_security_active('public.places'::regclass)
            from pg_roles r
            where r.rolname = current_user
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseRuntimeRoleGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        Boolean safeRuntimeRole = jdbcTemplate.queryForObject(SAFE_RUNTIME_ROLE_QUERY, Boolean.class);
        if (!Boolean.TRUE.equals(safeRuntimeRole)) {
            throw new IllegalStateException(
                    "PostgreSQL runtime role must not be superuser or BYPASSRLS and must be subject to RLS");
        }
    }
}
