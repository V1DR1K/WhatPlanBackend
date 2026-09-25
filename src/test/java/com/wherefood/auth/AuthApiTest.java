package com.wherefood.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralJwt;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class AuthApiTest {
    private final CentralAuthClient central = mock(CentralAuthClient.class);
    private final CentralJwt jwt = mock(CentralJwt.class);
    private final LocalUserProvisioner provisioner = mock(LocalUserProvisioner.class);
    private final AuthApi api = new AuthApi(central, jwt, provisioner, Set.of("https://whatplan.test"), 604800);

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

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse result = api.login(new LoginRequest("new-user", "password"), response);

        verify(central).login("new-user", "password");
        verify(provisioner).provision(userId, "new-user");
        org.junit.jupiter.api.Assertions.assertEquals("new-user", result.username());
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void loginRejectsInactiveCentralAccountBeforeLocalProvisioning() {
        UUID userId = UUID.randomUUID();
        when(central.login("new-user", "password")).thenReturn(new CentralAuthClient.TokenResponse(
                "access", "refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "new-user", "DISABLED", null, null, false)));

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> api.login(new LoginRequest("new-user", "password"), new MockHttpServletResponse()));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(provisioner, org.mockito.Mockito.never()).provision(userId, "new-user");
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

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthResponse result = api.refresh("refresh-token", sameOriginRequest(), response);

        verify(provisioner).provision(userId, "intruder");
        org.junit.jupiter.api.Assertions.assertEquals("intruder", result.username());
        org.junit.jupiter.api.Assertions.assertNull(result.refreshToken());
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Lax"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=604800"));
        org.junit.jupiter.api.Assertions.assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void reportsInvalidTokenFromCentralAuthAsBadGatewayNotUserUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(central.login("new-user", "password")).thenReturn(new CentralAuthClient.TokenResponse(
                "invalid-access", "refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "new-user", "ACTIVE", null, null, false)));
        when(jwt.subject("invalid-access")).thenThrow(new IllegalArgumentException("invalid claims"));

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> api.login(new LoginRequest("new-user", "password"), new MockHttpServletResponse()));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void rejectsCrossOriginRefreshBeforeCallingCentralService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://attacker.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> api.refresh("refresh-token", request, response));

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals("no-store", response.getHeader("Cache-Control"));
        org.mockito.Mockito.verifyNoInteractions(central);
    }

    @Test
    void logoutClearsTheCookieEvenWhenCentralRevocationFails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY))
                .when(central).logout("refresh-token");

        org.junit.jupiter.Assertions.assertThrows(ResponseStatusException.class,
                () -> api.logout("refresh-token", sameOriginRequest(), response));

        org.junit.jupiter.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        org.junit.jupiter.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
        org.junit.jupiter.Assertions.assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void preservesTheRotatedCookieIfLocalProvisioningFailsAfterCentralRotation() {
        UUID userId = UUID.randomUUID();
        when(central.refresh("old-refresh")).thenReturn(new CentralAuthClient.TokenResponse(
                "access", "rotated-refresh", "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "new-user", "ACTIVE", null, null, false)));
        when(jwt.subject("access")).thenReturn(userId);
        when(provisioner.provision(userId, "new-user")).thenThrow(new IllegalStateException("database unavailable"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.junit.jupiter.Assertions.assertThrows(IllegalStateException.class,
                () -> api.refresh("old-refresh", sameOriginRequest(), response));

        org.junit.jupiter.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("rotated-refresh"));
        org.junit.jupiter.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=604800"));
    }

    @Test
    void rejectsRefreshResponsesThatDoNotRotateAndDeletesTheOldCookie() {
        UUID userId = UUID.randomUUID();
        when(central.refresh("old-refresh")).thenReturn(new CentralAuthClient.TokenResponse(
                "access", null, "Bearer", 300,
                new CentralAuthClient.CentralUser(userId, "new-user", "ACTIVE", null, null, false)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseStatusException error = org.junit.jupiter.Assertions.assertThrows(ResponseStatusException.class,
                () -> api.refresh("old-refresh", sameOriginRequest(), response));

        org.junit.jupiter.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        org.junit.jupiter.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        org.mockito.Mockito.verifyNoInteractions(jwt, provisioner);
    }

    private static MockHttpServletRequest sameOriginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://whatplan.test");
        return request;
    }
}
