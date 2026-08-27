package com.wherefood.auth;

import com.wherefood.config.CentralAuthClient;
import com.wherefood.config.CentralAuthClient.CentralUser;
import com.wherefood.config.CentralAuthClient.TokenResponse;
import com.wherefood.config.CentralJwt;
import com.wherefood.config.AllowedWhatPlanUsers;
import com.wherefood.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final AllowedWhatPlanUsers allowedUsers;

    public AuthApi(CentralAuthClient central, CentralJwt jwt, LocalUserProvisioner provisioner,
                   AllowedWhatPlanUsers allowedUsers) {
        this.central = central;
        this.jwt = jwt;
        this.provisioner = provisioner;
        this.allowedUsers = allowedUsers;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        allowedUsers.requireAllowed(request.username());
        TokenResponse response = central.login(request.username(), request.password());
        return authenticatedResponse(response);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authenticatedResponse(central.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    CentralAuthClient.MessageResponse logout(@Valid @RequestBody LogoutRequest request) {
        central.logout(request.refreshToken());
        return new CentralAuthClient.MessageResponse("Logged out");
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

    private AuthResponse authenticatedResponse(TokenResponse response) {
        if (response == null || response.accessToken() == null || response.user() == null) {
            throw new IllegalStateException("Central authentication response is incomplete");
        }
        UUID subject = jwt.subject(response.accessToken());
        CentralUser centralUser = response.user();
        allowedUsers.requireAllowed(centralUser.username());
        if (!subject.equals(centralUser.id())) {
            throw new IllegalStateException("Central JWT subject does not match its user");
        }
        User localUser = provisioner.provision(subject, centralUser.username());
        return new AuthResponse(response.accessToken(), localUser.username, localUser.role.name(),
                response.accessToken(), response.refreshToken(), response.tokenType(), response.expiresIn(),
                info(localUser, centralUser.mustChangePassword()));
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
