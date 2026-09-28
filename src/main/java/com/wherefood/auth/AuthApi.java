package com.wherefood.auth;

import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralAuthClient.CentralUser;
import com.wherefood.config.CentralAuthClient.TokenResponse;
import com.wherefood.config.CentralJwt;
import com.wherefood.config.AllowedWhatPlanUsers;
import com.wherefood.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record LoginRequest(@NotBlank @jakarta.validation.constraints.Size(max = 80) String username, @NotBlank @jakarta.validation.constraints.Size(max = 200) String password) {}
record RefreshRequest(@Size(max = 4096) String refreshToken) {}
record LogoutRequest(@Size(max = 4096) String refreshToken) {}
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
    private final AllowedWhatPlanUsers allowedUsers;
    private final Set<String> cookieAllowedOrigins;
    private final Duration refreshCookieTtl;

    public AuthApi(CentralAuthClient central, CentralJwt jwt, LocalUserProvisioner provisioner,
                   AllowedWhatPlanUsers allowedUsers,
                   @Value("${AUTH_COOKIE_ALLOWED_ORIGINS}") String allowedOrigins,
                   @Value("${AUTH_REFRESH_COOKIE_TTL_SECONDS:604800}") long refreshCookieTtlSeconds) {
        if (refreshCookieTtlSeconds < 1 || refreshCookieTtlSeconds > 2_592_000) {
            throw new IllegalStateException("AUTH_REFRESH_COOKIE_TTL_SECONDS must be between 1 and 2592000");
        }
        this.central = central;
        this.jwt = jwt;
        this.provisioner = provisioner;
        this.allowedUsers = allowedUsers;
        this.cookieAllowedOrigins = parseOrigins(allowedOrigins);
        this.refreshCookieTtl = Duration.ofSeconds(refreshCookieTtlSeconds);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse servletResponse) {
        noStore(servletResponse);
        allowedUsers.requireAllowed(request.username());
        TokenResponse tokenResponse = central.login(request.username(), request.password());
        return authenticatedResponse(tokenResponse, servletResponse);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@CookieValue(name = "whatplan_refresh", required = false) String cookie,
                         @Valid @RequestBody(required = false) RefreshRequest body,
                         HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        requireAllowedOrigin(request);
        String refreshToken = present(cookie) ? cookie : body == null ? null : body.refreshToken();
        if (!validRefreshToken(refreshToken)) {
            clearRefreshCookie(response);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token requerido");
        }
        try {
            return authenticatedResponse(central.refresh(refreshToken), response);
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 403) {
                clearRefreshCookie(response);
            }
            throw exception;
        }
    }

    @PostMapping("/logout")
    CentralAuthClient.MessageResponse logout(@CookieValue(name = "whatplan_refresh", required = false) String cookie,
                                             @Valid @RequestBody(required = false) LogoutRequest body,
                                             HttpServletRequest request, HttpServletResponse response) {
        noStore(response);
        requireAllowedOrigin(request);
        String refreshToken = present(cookie) ? cookie : body == null ? null : body.refreshToken();
        try {
            if (validRefreshToken(refreshToken)) central.logout(refreshToken);
            return new CentralAuthClient.MessageResponse("Logged out");
        } finally {
            clearRefreshCookie(response);
        }
    }

    @GetMapping("/me")
    LocalUserInfo me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                     @AuthenticationPrincipal User localUser) {
        CentralAuthClient.MeResponse centralUser = central.me(authorization);
        allowedUsers.requireAllowed(centralUser.username());
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
        noStore(servletResponse);
        if (response == null || response.accessToken() == null || response.user() == null) {
            clearRefreshCookie(servletResponse);
            throw new IllegalStateException("Central authentication response is incomplete");
        }
        if (!validRefreshToken(response.refreshToken())) {
            clearRefreshCookie(servletResponse);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Central authentication did not return a refresh token");
        }
        UUID subject = jwt.subject(response.accessToken());
        CentralUser centralUser = response.user();
        allowedUsers.requireAllowed(centralUser.username());
        if (!subject.equals(centralUser.id())) {
            throw new IllegalStateException("Central JWT subject does not match its user");
        }
        User localUser = provisioner.provision(subject, centralUser.username());
        setRefreshCookie(servletResponse, response.refreshToken());
        return new AuthResponse(response.accessToken(), localUser.username, localUser.role.name(),
                response.accessToken(), null, response.tokenType(), response.expiresIn(),
                info(localUser, centralUser.mustChangePassword()));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("whatplan_refresh", token)
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth")
                .maxAge(refreshCookieTtl).build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("whatplan_refresh", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth").maxAge(0).build().toString());
    }

    private void requireAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowed = false;
        if (origin != null) {
            try {
                allowed = cookieAllowedOrigins.contains(normalizeOrigin(origin));
            } catch (IllegalStateException ignored) {
                // Invalid browser origins are treated as cross-site requests.
            }
        }
        if (!allowed) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Origen no permitido para operar la sesión");
        }
    }

    private static Set<String> parseOrigins(String values) {
        if (values == null || values.isBlank()) {
            throw new IllegalStateException("AUTH_COOKIE_ALLOWED_ORIGINS must contain exact origins");
        }
        return Arrays.stream(values.split(",")).map(String::trim).map(AuthApi::normalizeOrigin)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeOrigin(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()
                    && !"/".equals(uri.getPath()))
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Origin must be an exact HTTP(S) origin");
            }
            int port = uri.getPort();
            if (("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443)) port = -1;
            return new URI(scheme.toLowerCase(java.util.Locale.ROOT), null,
                    host.toLowerCase(java.util.Locale.ROOT), port, null, null, null).toString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalStateException("AUTH_COOKIE_ALLOWED_ORIGINS or request Origin is invalid", exception);
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validRefreshToken(String value) {
        return present(value) && value.length() <= 4096;
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
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
