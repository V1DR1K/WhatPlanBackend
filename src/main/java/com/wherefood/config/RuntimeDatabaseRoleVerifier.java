package com.wherefood.config;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Prevents the application from starting with a database role that bypasses RLS. */
@Component
public class RuntimeDatabaseRoleVerifier implements ApplicationRunner {
    private static final String ROLE_QUERY = "select current_user as role_name, "
            + "rolsuper as superuser, rolbypassrls as bypass_rls "
            + "from pg_roles where rolname = current_user";

    private final JdbcTemplate jdbc;

    public RuntimeDatabaseRoleVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> role = jdbc.queryForMap(ROLE_QUERY);
        boolean superuser = Boolean.TRUE.equals(role.get("superuser"));
        boolean bypassRls = Boolean.TRUE.equals(role.get("bypass_rls"));
        if (superuser || bypassRls) {
            throw new IllegalStateException("Runtime PostgreSQL role must not have SUPERUSER or BYPASSRLS");
        }
    }
}
