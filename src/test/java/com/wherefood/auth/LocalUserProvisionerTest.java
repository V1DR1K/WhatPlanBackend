package com.wherefood.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Users;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LocalUserProvisionerTest {
    private final Users users = mock(Users.class);
    private final LocalUserProvisioner provisioner = new LocalUserProvisioner(users, "USER");

    @Test
    void refusesAdminAsAutomaticProvisioningRole() {
        assertThrows(IllegalArgumentException.class, () -> new LocalUserProvisioner(users, "ADMIN"));
    }

    @Test
    void provision_tomasUsernameWithDifferentCentralId_requiresVerifiedMigration() {
        rejectsLegacyUsernameTakeover("tomas");
    }

    @Test
    void provision_avrilUsernameWithDifferentCentralId_requiresVerifiedMigration() {
        rejectsLegacyUsernameTakeover("avril");
    }

    private void rejectsLegacyUsernameTakeover(String username) {
        UUID newCentralId = UUID.randomUUID();
        User legacy = new User();
        legacy.id = 10L;
        legacy.username = username;
        legacy.role = Role.ADMIN;
        when(users.findByAuthUserId(newCentralId)).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(legacy));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> provisioner.provision(newCentralId, username));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(null, legacy.authUserId);
        assertEquals(Role.ADMIN, legacy.role);
        verify(users, never()).save(legacy);
    }
}
