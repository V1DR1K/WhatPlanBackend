package com.wherefood.config;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseRuntimeRoleGuardTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void continuesStartupWhenRuntimeRoleCannotBypassRowSecurity() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);

        assertThatNoException().isThrownBy(() -> guard().afterPropertiesSet());
    }

    @Test
    void failsStartupWhenRuntimeRoleIsSuperuserOrCanBypassRls() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

        assertThatThrownBy(() -> guard().afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be superuser or BYPASSRLS");
    }

    @Test
    void failsStartupWhenRuntimeRoleIsMissingOrCannotQueryRoleStatus() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(null);

        assertThatThrownBy(() -> guard().afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    private DatabaseRuntimeRoleGuard guard() {
        return new DatabaseRuntimeRoleGuard(jdbcTemplate);
    }
}
