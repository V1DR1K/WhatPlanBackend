package com.wherefood.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralJwt;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthApiTest {
    private final CentralAuthClient central = mock(CentralAuthClient.class);
    private final CentralJwt jwt = mock(CentralJwt.class);
    private final LocalUserProvisioner provisioner = mock(LocalUserProvisioner.class);
    private final AuthApi api = new AuthApi(central, jwt, provisioner);

    @Test
    void provisionsAUserReturnedByCentralLoginWithoutAnAllowlist() {
        UUID userId = UUID.randomUUID();
        CentralAuthClient.TokenResponse response = new CentralAuthClient.TokenResponse(
                "access", "refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "new-user", "ACTIVE", null, null, false));
        User local = new User();
        local.username = "new-user";
        local.role = Role.USER;
        when(central.login("new-user", "password")).thenReturn(response);
        when(jwt.subject("access")).thenReturn(userId);
        when(provisioner.provision(userId, "new-user")).thenReturn(local);

        AuthResponse result = api.login(new LoginRequest("new-user", "password"), new MockHttpServletResponse());

        verify(central).login("new-user", "password");
        verify(provisioner).provision(userId, "new-user");
        org.junit.jupiter.api.Assertions.assertEquals("new-user", result.username());
    }

    @Test
    void refreshesAnAuthenticatedUserAndDoesNotReturnRefreshTokenInJson() {
        UUID userId = UUID.randomUUID();
        CentralAuthClient.TokenResponse response = new CentralAuthClient.TokenResponse(
                "access", "refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "intruder", "ACTIVE", null, null, false));
        org.mockito.Mockito.when(central.refresh("refresh-token")).thenReturn(response);
        org.mockito.Mockito.when(jwt.subject("access")).thenReturn(userId);

        User local = new User();
        local.username = "intruder";
        local.role = Role.USER;
        when(provisioner.provision(userId, "intruder")).thenReturn(local);

        AuthResponse result = api.refresh(new RefreshRequest("refresh-token"), null, new MockHttpServletResponse());

        verify(provisioner).provision(userId, "intruder");
        org.junit.jupiter.api.Assertions.assertEquals("intruder", result.username());
        org.junit.jupiter.api.Assertions.assertNull(result.refreshToken());
    }
}
