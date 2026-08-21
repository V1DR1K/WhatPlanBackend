package com.wherefood.config;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CentralAuthClient {
    private final RestClient client;

    public CentralAuthClient(RestClient.Builder builder, @Value("${app.auth-service-url}") String serviceUrl) {
        this.client = builder.baseUrl(serviceUrl).build();
    }

    public TokenResponse login(String username, String password) {
        return post("/api/login", new LoginRequest(username, password), null, TokenResponse.class);
    }

    public TokenResponse refresh(String refreshToken) {
        return post("/api/refresh", new RefreshRequest(refreshToken), null, TokenResponse.class);
    }

    public void logout(String refreshToken) {
        post("/api/logout", new RefreshRequest(refreshToken), null, MessageResponse.class);
    }

    public MeResponse me(String authorization) {
        return get("/api/me", authorization, MeResponse.class);
    }

    public MessageResponse changePassword(String authorization, String currentPassword, String newPassword) {
        return post("/api/change-password", new ChangePasswordRequest(currentPassword, newPassword), authorization, MessageResponse.class);
    }

    private <T> T get(String path, String authorization, Class<T> responseType) {
        try {
            return client.get().uri(path).header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw upstreamFailure(ex);
        }
    }

    private <T> T post(String path, Object request, String authorization, Class<T> responseType) {
        try {
            var call = client.post().uri(path).body(request);
            if (authorization != null) call.header(HttpHeaders.AUTHORIZATION, authorization);
            return call.retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw upstreamFailure(ex);
        }
    }

    private static org.springframework.web.server.ResponseStatusException upstreamFailure(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return new org.springframework.web.server.ResponseStatusException(status == null ? HttpStatus.BAD_GATEWAY : status,
                "Central authentication service request failed");
    }

    public record LoginRequest(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, CentralUser user) {}
    public record CentralUser(UUID id, String username, String status, Instant created, Instant lastLogin, boolean mustChangePassword) {}
    public record MeResponse(UUID id, String username, boolean mustChangePassword) {}
    public record MessageResponse(String message) {}
}
