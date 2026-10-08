package com.wherefood.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Users;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LocalUserProvisionerTest {
    @Mock
    private Users users;

    private LocalUserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new LocalUserProvisioner(users, "USER");
    }

    @Test
    void provision_whenDefaultRoleIsAdmin_rejectsAutomaticAdminProvisioning() {
        assertThatThrownBy(() -> new LocalUserProvisioner(users, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provision_whenNewCentralIdentityClaimsLegacyTomas_requiresVerifiedMigration() {
        rejectsLegacyUsernameTakeover("tomas");
    }

    @Test
    void provision_whenNewCentralIdentityClaimsLegacyAvril_requiresVerifiedMigration() {
        rejectsLegacyUsernameTakeover("avril");
    }

    @Test
    void provision_whenLinkedUserIsAdmin_preservesAdminRole() {
        UUID centralId = UUID.randomUUID();
        User tomas = new User();
        tomas.id = 10L;
        tomas.username = "tomas";
        tomas.authUserId = centralId;
        tomas.role = Role.ADMIN;
        when(users.findByAuthUserId(centralId)).thenReturn(Optional.of(tomas));
        when(users.findByUsernameIgnoreCase("tomas")).thenReturn(Optional.of(tomas));
        when(users.save(tomas)).thenReturn(tomas);

        User provisioned = provisioner.provision(centralId, "tomas");

        assertThat(provisioned.role).isEqualTo(Role.ADMIN);
        assertThat(provisioned.authUserId).isEqualTo(centralId);
        verify(users).save(tomas);
    }

    private void rejectsLegacyUsernameTakeover(String username) {
        UUID newCentralId = UUID.randomUUID();
        User legacy = new User();
        legacy.id = 10L;
        legacy.username = username;
        legacy.role = Role.ADMIN;
        when(users.findByAuthUserId(newCentralId)).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> provisioner.provision(newCentralId, username))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(legacy.authUserId).isNull();
        assertThat(legacy.role).isEqualTo(Role.ADMIN);
        verify(users, never()).save(legacy);
    }
}
