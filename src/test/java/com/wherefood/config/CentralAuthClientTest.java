package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class CentralAuthClientTest {
    @Test
    void mapsUnavailableAuthenticationServiceWithoutExposingItsResponseBody() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] privateBody = "upstream database password leaked".getBytes(StandardCharsets.UTF_8);
        upstream.createContext("/api/login", exchange -> {
            exchange.sendResponseHeaders(HttpStatus.SERVICE_UNAVAILABLE.value(), privateBody.length);
            try (var response = exchange.getResponseBody()) {
                response.write(privateBody);
            }
        });
        upstream.start();
        try {
            CentralAuthClient client = new CentralAuthClient(RestClient.builder(),
                    "http://127.0.0.1:" + upstream.getAddress().getPort(), 1, 1, 1);

            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> client.login("alice", "not-a-real-password"));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
            assertEquals("Central authentication service request failed", failure.getReason());
            assertFalse(failure.getMessage().contains("upstream database password leaked"));
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void rejectsCallsAboveConfiguredConcurrencyAndReleasesThePermit() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch requestEntered = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        upstream.createContext("/api/login", exchange -> {
            requestEntered.countDown();
            try {
                if (!releaseRequest.await(3, TimeUnit.SECONDS)) {
                    exchange.sendResponseHeaders(504, -1);
                    return;
                }
                byte[] body = "{\"accessToken\":\"a\",\"refreshToken\":\"r\",\"tokenType\":\"Bearer\",\"expiresIn\":300,\"user\":null}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var response = exchange.getResponseBody()) { response.write(body); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        upstream.start();
        var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            CentralAuthClient client = new CentralAuthClient(RestClient.builder(),
                    "http://127.0.0.1:" + upstream.getAddress().getPort(), 1, 4, 1);
            CompletableFuture<CentralAuthClient.TokenResponse> first = CompletableFuture.supplyAsync(
                    () -> client.login("alice", "password"), caller);
            org.junit.jupiter.api.Assertions.assertTrue(requestEntered.await(2, TimeUnit.SECONDS));

            ResponseStatusException busy = assertThrows(ResponseStatusException.class,
                    () -> client.login("bob", "password"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
            assertEquals(1L, ((RetryAfterResponseStatusException) busy).retryAfterSeconds());

            releaseRequest.countDown();
            first.get(3, TimeUnit.SECONDS);
            assertEquals("a", client.login("bob", "password").accessToken());
            // A successful second call proves the permit was released after the first response.
        } finally {
            releaseRequest.countDown();
            caller.shutdownNow();
            upstream.stop(0);
        }
    }
}
