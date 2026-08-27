package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AllowedWhatPlanUsersTest {
    private final AllowedWhatPlanUsers users = new AllowedWhatPlanUsers("tomas, avril");

    @Test
    void matchesConfiguredUsersIgnoringCaseAndWhitespace() {
        assertTrue(users.isAllowed(" TOMAS "));
        assertTrue(users.isAllowed("avril"));
        assertFalse(users.isAllowed("someone-else"));
    }

    @Test
    void rejectsUnconfiguredUsers() {
        assertThrows(ResponseStatusException.class, () -> users.requireAllowed("someone-else"));
    }
}
