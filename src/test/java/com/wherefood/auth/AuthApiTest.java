package com.wherefood.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wherefood.config.AllowedWhatPlanUsers;
import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralJwt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AuthApiTest {
    private final CentralAuthClient central = mock(CentralAuthClient.class);
    private final CentralJwt jwt = mock(CentralJwt.class);
    private final LocalUserProvisioner provisioner = mock(LocalUserProvisioner.class);
    private final AuthApi api = new AuthApi(central, jwt, provisioner, new AllowedWhatPlanUsers("tomas,avril"));

    @Test
    void rejectsAnUnlistedUserBeforeCallingCentralLogin() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> api.login(new LoginRequest("intruder", "password")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(central, never()).login("intruder", "password");
    }

    @Test
    void rejectsAnUnlistedUserReturnedByRefresh() {
        UUID userId = UUID.randomUUID();
        CentralAuthClient.TokenResponse response = new CentralAuthClient.TokenResponse(
                "access", "refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "intruder", "ACTIVE", null, null, false));
        org.mockito.Mockito.when(central.refresh("refresh-token")).thenReturn(response);
        org.mockito.Mockito.when(jwt.subject("access")).thenReturn(userId);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> api.refresh(new RefreshRequest("refresh-token")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(provisioner, never()).provision(userId, "intruder");
    }
}
