package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
                    "http://127.0.0.1:" + upstream.getAddress().getPort(), 1, 1);

            ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                    () -> client.login("alice", "not-a-real-password"));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
            assertEquals("Central authentication service request failed", failure.getReason());
            assertFalse(failure.getMessage().contains("upstream database password leaked"));
        } finally {
            upstream.stop(0);
        }
    }
}
