package com.wherefood.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class RuntimeDatabaseRoleVerifierTest {
    private static final String ROLE_QUERY = "select current_user as role_name, "
            + "rolsuper as superuser, rolbypassrls as bypass_rls "
            + "from pg_roles where rolname = current_user";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void run_whenRuntimeRoleIsRestricted_startsNormally() {
        when(jdbc.queryForMap(ROLE_QUERY)).thenReturn(Map.of(
                "role_name", "whatplan_runtime",
                "superuser", false,
                "bypass_rls", false));

        assertThatCode(() -> verifier().run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void run_whenRuntimeRoleIsSuperuser_failsClosed() {
        when(jdbc.queryForMap(ROLE_QUERY)).thenReturn(Map.of(
                "role_name", "postgres",
                "superuser", true,
                "bypass_rls", false));

        assertThatThrownBy(() -> verifier().run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPERUSER or BYPASSRLS");
    }

    @Test
    void run_whenRuntimeRoleHasBypassRls_failsClosed() {
        when(jdbc.queryForMap(ROLE_QUERY)).thenReturn(Map.of(
                "role_name", "whatplan_runtime",
                "superuser", false,
                "bypass_rls", true));

        assertThatThrownBy(() -> verifier().run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPERUSER or BYPASSRLS");
    }

    private RuntimeDatabaseRoleVerifier verifier() {
        return new RuntimeDatabaseRoleVerifier(jdbc);
    }
}
