package com.wherefood.auth;

import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralAuthClient.CentralUser;
import com.wherefood.config.CentralAuthClient.TokenResponse;
import com.wherefood.config.CentralJwt;
import com.wherefood.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record LoginRequest(@NotBlank @jakarta.validation.constraints.Size(max = 80) String username, @NotBlank @jakarta.validation.constraints.Size(max = 200) String password) {}
record RefreshRequest(@NotBlank @jakarta.validation.constraints.Size(max = 4096) String refreshToken) {}
record LogoutRequest(@NotBlank @jakarta.validation.constraints.Size(max = 4096) String refreshToken) {}
record ChangePasswordRequest(@NotBlank @jakarta.validation.constraints.Size(max = 200) String currentPassword, @NotBlank @jakarta.validation.constraints.Size(max = 200) String newPassword) {}
record LocalUserInfo(Long id, UUID authUserId, String username, String role, boolean mustChangePassword) {}
record AuthResponse(String token, String username, String role, String accessToken, String refreshToken,
                    String tokenType, long expiresIn, LocalUserInfo user) {}

@RestController
@RequestMapping("/api/auth")
public class AuthApi {
    private final CentralAuthClient central;
    private final CentralJwt jwt;
    private final LocalUserProvisioner provisioner;
    private final boolean secureCookies;

    public AuthApi(CentralAuthClient central, CentralJwt jwt, LocalUserProvisioner provisioner) {
        this(central, jwt, provisioner, true);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthApi(CentralAuthClient central, CentralJwt jwt, LocalUserProvisioner provisioner,
                   @Value("${app.auth-cookie-secure:true}") boolean secureCookies) {
        this.central = central;
        this.jwt = jwt;
        this.provisioner = provisioner;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse servletResponse) {
        TokenResponse tokenResponse = central.login(request.username(), request.password());
        return authenticatedResponse(tokenResponse, servletResponse);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@RequestBody(required = false) @Valid RefreshRequest request,
                         @CookieValue(name = "whatplan_refresh", required = false) String cookie,
                         HttpServletResponse response) {
        String refreshToken = cookie != null ? cookie : request == null ? null : request.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Refresh token requerido");
        return authenticatedResponse(central.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    CentralAuthClient.MessageResponse logout(@RequestBody(required = false) @Valid LogoutRequest request,
                                             @CookieValue(name = "whatplan_refresh", required = false) String cookie,
                                             HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String refreshToken = cookie != null ? cookie : request == null ? null : request.refreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) central.logout(refreshToken);
        clearRefreshCookie(response);
        return new CentralAuthClient.MessageResponse("Logged out");
    }

    @GetMapping("/me")
    LocalUserInfo me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                     @AuthenticationPrincipal User localUser) {
        CentralAuthClient.MeResponse centralUser = central.me(authorization);
        verifySubject(centralUser.id(), localUser);
        User synchronizedUser = provisioner.provision(centralUser.id(), centralUser.username());
        return info(synchronizedUser, centralUser.mustChangePassword());
    }

    @PostMapping({"/change-password", "/password"})
    CentralAuthClient.MessageResponse changePassword(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        return central.changePassword(authorization, request.currentPassword(), request.newPassword());
    }

    private AuthResponse authenticatedResponse(TokenResponse response, HttpServletResponse servletResponse) {
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        servletResponse.setHeader("Pragma", "no-cache");
        if (response == null || response.accessToken() == null || response.user() == null) {
            throw new IllegalStateException("Central authentication response is incomplete");
        }
        UUID subject = jwt.subject(response.accessToken());
        CentralUser centralUser = response.user();
        if (!subject.equals(centralUser.id())) {
            throw new IllegalStateException("Central JWT subject does not match its user");
        }
        User localUser = provisioner.provision(subject, centralUser.username());
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) setRefreshCookie(servletResponse, response.refreshToken());
        return new AuthResponse(response.accessToken(), localUser.username, localUser.role.name(),
                response.accessToken(), null, response.tokenType(), response.expiresIn(),
                info(localUser, centralUser.mustChangePassword()));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("whatplan_refresh", token)
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/auth")
                .maxAge(java.time.Duration.ofDays(7)).build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("whatplan_refresh", "")
                .httpOnly(true).secure(secureCookies).sameSite("Lax").path("/api/auth").maxAge(0).build().toString());
    }

    private static void verifySubject(UUID subject, User localUser) {
        if (subject == null || localUser == null || !subject.equals(localUser.authUserId)) {
            throw new IllegalStateException("Central JWT subject does not match the local user");
        }
    }

    private static LocalUserInfo info(User user, boolean mustChangePassword) {
        return new LocalUserInfo(user.id, user.authUserId, user.username, user.role.name(), mustChangePassword);
    }
}
