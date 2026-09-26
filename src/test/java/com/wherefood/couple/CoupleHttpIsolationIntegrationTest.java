package com.wherefood.couple;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Exercises the production HTTP security chain, JWT resolution, tenant context and PostgreSQL RLS together. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CoupleHttpIsolationIntegrationTest.ErrorTestConfiguration.class)
class CoupleHttpIsolationIntegrationTest {
    private static final String ISSUER = "whatplan-http-test-issuer";
    private static final String AUDIENCE = "whatplan-http-test";
    private static final String ADMIN_PASSWORD = "test-only-admin-password-0123456789";
    private static final String REDIS_PASSWORD = "test-only-redis-password-0123456789";
    private static final UUID USER_A1_AUTH_ID = UUID.fromString("3c894d62-8681-42d5-bfaa-7e9a9ef02001");
    private static final UUID USER_A2_AUTH_ID = UUID.fromString("3c894d62-8681-42d5-bfaa-7e9a9ef02002");
    private static final UUID USER_B_AUTH_ID = UUID.fromString("3c894d62-8681-42d5-bfaa-7e9a9ef02003");
    private static final UUID ADMIN_AUTH_ID = UUID.fromString("3c894d62-8681-42d5-bfaa-7e9a9ef02004");
    private static final UUID ORPHAN_AUTH_ID = UUID.fromString("3c894d62-8681-42d5-bfaa-7e9a9ef02005");
    private static final UUID COUPLE_A_ID = UUID.fromString("c8f36af3-4259-4648-aace-dd135a9f0101");
    private static final UUID COUPLE_B_ID = UUID.fromString("c8f36af3-4259-4648-aace-dd135a9f0102");
    private static final KeyPair JWT_KEYS = newRsaKeyPair();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("whatplan_test")
                    .withUsername("whatplan_admin")
                    .withPassword(ADMIN_PASSWORD)
                    .withInitScript("db/couple-http-role-bootstrap.sql");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorTestConfiguration {
        @Bean
        ErrorTestController errorTestController() {
            return new ErrorTestController();
        }
    }

    @RestController
    static class ErrorTestController {
        @GetMapping("/__test/internal-error")
        String internalError() {
            throw new IllegalStateException("sensitive test failure detail");
        }

        @GetMapping("/__test/upload-error")
        String uploadError() {
            throw new MaxUploadSizeExceededException(10L);
        }
    }

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry properties) {
        properties.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        properties.add("DATABASE_USER", () -> "whatplan_runtime");
        properties.add("DATABASE_PASSWORD", () -> "test-only-runtime-password-0123456789");
        properties.add("DATABASE_ADMIN_USER", POSTGRES::getUsername);
        properties.add("DATABASE_MIGRATION_USER", () -> "whatplan_migrator");
        properties.add("DATABASE_MIGRATION_PASSWORD", () -> "test-only-migration-password-0123456789");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", () -> "whatplan_migrator");
        properties.add("spring.flyway.password", () -> "test-only-migration-password-0123456789");
        properties.add("REDIS_HOST", REDIS::getHost);
        properties.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
        properties.add("REDIS_PASSWORD", () -> REDIS_PASSWORD);
        properties.add("AUTH_SERVICE_URL", () -> "https://central-auth.example.test");
        properties.add("AUTH_PUBLIC_KEY_PEM", CoupleHttpIsolationIntegrationTest::publicPem);
        properties.add("AUTH_JWT_ISSUER", () -> ISSUER);
        properties.add("AUTH_JWT_AUDIENCE", () -> AUDIENCE);
        properties.add("AUTH_DEFAULT_ROLE", () -> "USER");
        properties.add("AUTH_COOKIE_ALLOWED_ORIGINS", () -> "https://whatplan.example.test");
        properties.add("AUTH_COOKIE_SECURE", () -> "true");
        properties.add("AUTH_REFRESH_COOKIE_TTL_SECONDS", () -> "604800");
        properties.add("TMDB_READ_ACCESS_TOKEN", () -> "test-only-tmdb-token");
    }

    @Test
    void productionHttpChainKeepsReadsWritesReviewsAndPhotosInsideTheAuthenticatedCouple() throws Exception {
        Fixture fixture = seedFixture();

        ResponseEntity<String> unauthenticated = http.getForEntity(url("/api/places"), String.class);
        JsonNode unauthenticatedProblem = objectMapper.readTree(unauthenticated.getBody());
        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthenticated.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(unauthenticatedProblem.path("type").asText()).isEqualTo("about:blank");
        assertThat(unauthenticatedProblem.path("status").asInt()).isEqualTo(401);
        assertThat(unauthenticatedProblem.path("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(unauthenticated.getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(unauthenticatedProblem.path("requestId").asText());

        HttpHeaders invalidTokenHeaders = new HttpHeaders();
        invalidTokenHeaders.setBearerAuth("not-a-valid-jwt");
        assertProblem(http.exchange(url("/api/places"), HttpMethod.GET,
                new HttpEntity<>(invalidTokenHeaders), String.class), 401, "UNAUTHORIZED");

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> rateLimitedLogin = null;
        for (int attempt = 0; attempt < 11; attempt++) {
            rateLimitedLogin = http.postForEntity(url("/api/auth/login"),
                    new HttpEntity<>("{}", loginHeaders), String.class);
        }
        assertProblem(rateLimitedLogin, 429, "RATE_LIMITED");

        HttpHeaders invalidPlaceHeaders = authHeaders(USER_A1_AUTH_ID);
        invalidPlaceHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> invalidPlace = http.postForEntity(url("/api/places"), new HttpEntity<>("""
                {"name":"","address":"","sourceUrl":null,"mapsUrl":null,
                 "acceptsReservations":false,"categoryId":%d,"tagIds":[]}
                """.formatted(fixture.categoryId()), invalidPlaceHeaders), String.class);
        assertProblem(invalidPlace, 400, "VALIDATION_ERROR");

        assertProblem(get("/api/places?size=not-a-number", USER_A1_AUTH_ID, null), 400, "INVALID_REQUEST");
        assertProblem(get("/api/categories/all", USER_A1_AUTH_ID, null), 403, "FORBIDDEN");
        assertProblem(get("/api/not-a-real-route", USER_A1_AUTH_ID, null), 404, "NOT_FOUND");
        ResponseEntity<String> internalError = get("/__test/internal-error", USER_A1_AUTH_ID, null);
        assertProblem(internalError, 500, "INTERNAL_ERROR");
        assertThat(internalError.getBody()).doesNotContain("sensitive test failure detail");
        assertProblem(get("/__test/upload-error", USER_A1_AUTH_ID, null), 413, "UPLOAD_TOO_LARGE");
        assertProblem(postOversizedPhoto(fixture.placeA(), USER_A1_AUTH_ID), 413, "UPLOAD_TOO_LARGE");

        HttpHeaders unacceptableHeaders = authHeaders(USER_A1_AUTH_ID);
        unacceptableHeaders.setAccept(java.util.List.of(MediaType.APPLICATION_XML));
        ResponseEntity<String> unacceptable = http.exchange(url("/api/places"), HttpMethod.GET,
                new HttpEntity<>(unacceptableHeaders), String.class);
        assertProblem(unacceptable, 406, "NOT_ACCEPTABLE");

        HttpHeaders unsupportedHeaders = authHeaders(USER_A1_AUTH_ID);
        unsupportedHeaders.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> unsupported = http.exchange(url("/api/places"), HttpMethod.POST,
                new HttpEntity<>("not-json", unsupportedHeaders), String.class);
        assertProblem(unsupported, 415, "UNSUPPORTED_MEDIA_TYPE");

        assertProblem(http.exchange(url("/api/places/1/photo"), HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(USER_A1_AUTH_ID)), String.class), 405, "METHOD_NOT_ALLOWED");

        ResponseEntity<String> placesForA = get("/api/places", USER_A1_AUTH_ID, null);
        ResponseEntity<String> placesForB = get("/api/places", USER_B_AUTH_ID, null);
        assertThat(placesForA.getStatusCode().value()).isEqualTo(200);
        assertThat(placesForA.getBody()).contains("Private place A").doesNotContain("Private place B");
        assertThat(placesForB.getStatusCode().value()).isEqualTo(200);
        assertThat(placesForB.getBody()).contains("Private place B").doesNotContain("Private place A");

        ResponseEntity<String> archivedPlacesForA = get("/api/places/archived?size=1", USER_A1_AUTH_ID, null);
        ResponseEntity<String> archivedPlacesForB = get("/api/places/archived?size=1", USER_B_AUTH_ID, null);
        assertThat(archivedPlacesForA.getStatusCode().value()).isEqualTo(200);
        assertThat(archivedPlacesForA.getBody()).doesNotContain("Archived private place B");
        assertThat(archivedPlacesForB.getStatusCode().value()).isEqualTo(200);
        assertThat(archivedPlacesForB.getBody()).contains("Archived private place B");

        ResponseEntity<String> recipesForA = get("/api/how-cook/recipes?search=torta&home=TOMAS&cooked=true&sort=rating-asc&size=30", USER_A1_AUTH_ID, null);
        ResponseEntity<String> recipesForB = get("/api/how-cook/recipes?search=torta&home=AVRIL&cooked=true&sort=rating-desc&size=30", USER_B_AUTH_ID, null);
        assertThat(recipesForA.getStatusCode().value()).isEqualTo(200);
        assertThat(recipesForA.getBody()).contains("Torta pareja A").doesNotContain("Torta pareja B");
        assertThat(recipesForB.getStatusCode().value()).isEqualTo(200);
        assertThat(recipesForB.getBody()).contains("Torta pareja B").doesNotContain("Torta pareja A");

        ResponseEntity<String> activitiesForA = get("/api/why-fun/activities?search=museo&categoryId=" + fixture.activityCategoryId()
                + "&subcategoryId=" + fixture.activitySubcategoryId() + "&visited=true&sort=rating-asc&size=30",
                USER_A1_AUTH_ID, null);
        ResponseEntity<String> activitiesForB = get("/api/why-fun/activities?search=museo&categoryId=" + fixture.activityCategoryId()
                + "&subcategoryId=" + fixture.activitySubcategoryId() + "&visited=true&sort=rating-desc&size=30",
                USER_B_AUTH_ID, null);
        assertThat(activitiesForA.getStatusCode().value()).isEqualTo(200);
        assertThat(activitiesForA.getBody()).contains("Museo pareja A").doesNotContain("Museo pareja B");
        assertThat(activitiesForB.getStatusCode().value()).isEqualTo(200);
        assertThat(activitiesForB.getBody()).contains("Museo pareja B").doesNotContain("Museo pareja A");

        ResponseEntity<String> plansForA = get("/api/why-fun/plans?categoryId=" + fixture.activityCategoryId()
                + "&subcategoryId=" + fixture.activitySubcategoryId() + "&timeline=UNSCHEDULED&size=30",
                USER_A1_AUTH_ID, null);
        ResponseEntity<String> plansForB = get("/api/why-fun/plans?categoryId=" + fixture.activityCategoryId()
                + "&subcategoryId=" + fixture.activitySubcategoryId() + "&timeline=UNSCHEDULED&size=30",
                USER_B_AUTH_ID, null);
        assertThat(plansForA.getStatusCode().value()).isEqualTo(200);
        assertThat(plansForA.getBody()).contains("Museo pareja A").doesNotContain("Museo pareja B");
        assertThat(plansForB.getStatusCode().value()).isEqualTo(200);
        assertThat(plansForB.getBody()).contains("Museo pareja B").doesNotContain("Museo pareja A");

        ResponseEntity<String> filmsForA = get("/api/films?search=private&size=30", USER_A1_AUTH_ID, null);
        ResponseEntity<String> filmsForB = get("/api/films?search=private&size=30", USER_B_AUTH_ID, null);
        assertThat(filmsForA.getStatusCode().value()).isEqualTo(200);
        assertThat(filmsForA.getBody()).contains("Private film A").doesNotContain("Private film B");
        assertThat(filmsForB.getStatusCode().value()).isEqualTo(200);
        assertThat(filmsForB.getBody()).contains("Private film B").doesNotContain("Private film A");
        ResponseEntity<String> genreFilmsForA = get("/api/films?genre=Drama&search=private&size=1", USER_A1_AUTH_ID, null);
        ResponseEntity<String> genreFilmsForB = get("/api/films?genre=Drama&search=private&size=1", USER_B_AUTH_ID, null);
        assertThat(genreFilmsForA.getStatusCode().value()).isEqualTo(200);
        assertThat(genreFilmsForA.getBody()).contains("Private film A").doesNotContain("Private film B");
        assertThat(genreFilmsForB.getStatusCode().value()).isEqualTo(200);
        assertThat(genreFilmsForB.getBody()).contains("Private film B").doesNotContain("Private film A");

        ResponseEntity<String> calendarForA = get("/api/when-dates?size=12", USER_A1_AUTH_ID, null);
        ResponseEntity<String> calendarForB = get("/api/when-dates?size=12", USER_B_AUTH_ID, null);
        assertThat(calendarForA.getStatusCode().value()).isEqualTo(200);
        assertThat(calendarForA.getBody()).contains("Private anniversary A").doesNotContain("Private anniversary B");
        assertThat(calendarForB.getStatusCode().value()).isEqualTo(200);
        assertThat(calendarForB.getBody()).contains("Private anniversary B").doesNotContain("Private anniversary A");

        ResponseEntity<String> spoofedCoupleHeader = get("/api/places", USER_A1_AUTH_ID, COUPLE_B_ID.toString());
        assertThat(spoofedCoupleHeader.getStatusCode().value()).isEqualTo(200);
        assertThat(spoofedCoupleHeader.getBody()).contains("Private place A").doesNotContain("Private place B");

        assertThat(get("/api/places/" + fixture.placeA(), USER_A1_AUTH_ID, null).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> hiddenPlace = get("/api/places/" + fixture.placeB(), USER_A1_AUTH_ID, null);
        assertProblem(hiddenPlace, 404, "NOT_FOUND");
        assertThat(hiddenPlace.getBody()).doesNotContain("Private place B", "coupleId", "couple_id");
        assertThat(get("/api/places/" + fixture.placeA(), USER_B_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places/" + fixture.placeB() + "/visits", USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places/" + fixture.placeB() + "/item-dates", USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/items?placeId=" + fixture.placeB(), USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> attemptedCrossCoupleUpdate = putPlace(fixture.placeB(), USER_A1_AUTH_ID,
                "Attempted cross-couple edit", fixture.categoryId());
        assertThat(attemptedCrossCoupleUpdate.getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places/" + fixture.placeB(), USER_B_AUTH_ID, null).getBody())
                .contains("Private place B").doesNotContain("Attempted cross-couple edit");

        assertThat(putPlace(fixture.placeA(), USER_A2_AUTH_ID, "Shared edit by member A2", fixture.categoryId())
                .getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/places/" + fixture.placeA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Shared edit by member A2");

        ResponseEntity<String> createdByA = postPlace(USER_A1_AUTH_ID, COUPLE_B_ID.toString(),
                "New place created by A", fixture.categoryId());
        assertThat(createdByA.getStatusCode().value()).isEqualTo(200);
        Long createdPlaceId = objectMapper.readTree(createdByA.getBody()).path("id").asLong();
        assertThat(get("/api/places/" + createdPlaceId, USER_A1_AUTH_ID, null).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/places/" + createdPlaceId, USER_B_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);

        assertThat(get("/api/places/" + fixture.placeB() + "/photo", USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);
        ResponseEntity<byte[]> ownPhoto = getPhoto(fixture.placeB(), USER_B_AUTH_ID);
        assertThat(ownPhoto.getStatusCode().value()).isEqualTo(200);
        assertThat(ownPhoto.getHeaders().getCacheControl()).contains("no-store");
        assertThat(new String(ownPhoto.getBody(), StandardCharsets.UTF_8)).isEqualTo("private-photo-b");

        assertThat(putReview(fixture.placeA(), USER_A2_AUTH_ID, "Review from member two").getStatusCode().value())
                .isEqualTo(200);
        assertThat(putReview(fixture.placeA(), USER_B_AUTH_ID, "Cross-couple review").getStatusCode().value())
                .isEqualTo(404);
        assertReviewIsolation(fixture);

        assertThat(get("/api/places", ADMIN_AUTH_ID, null).getBody()).doesNotContain("Private place A", "Private place B");
        assertThat(get("/api/places/" + fixture.placeA(), ADMIN_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places", ORPHAN_AUTH_ID, null).getBody()).doesNotContain("Private place A", "Private place B");
        assertThat(get("/api/places/" + fixture.placeA(), ORPHAN_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);

        REDIS.stop();
        assertProblem(http.postForEntity(url("/api/auth/login"),
                new HttpEntity<>("{}", loginHeaders), String.class), 503, "RATE_LIMIT_UNAVAILABLE");
    }

    private ResponseEntity<String> get(String path, UUID subject, String spoofedCoupleId) {
        HttpHeaders headers = authHeaders(subject);
        if (spoofedCoupleId != null) headers.set("X-Couple-Id", spoofedCoupleId);
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postOversizedPhoto(Long placeId, UUID subject) throws IOException {
        String boundary = "----WhatPlanFixedLengthBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"oversized.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[10 * 1024 * 1024 + 1]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpURLConnection connection = (HttpURLConnection) new URL(url("/api/places/" + placeId + "/photo"))
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken(subject));
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setFixedLengthStreamingMode(body.size());
        try {
            connection.getOutputStream().write(body.toByteArray());
            int status = connection.getResponseCode();
            HttpHeaders headers = new HttpHeaders();
            String contentType = connection.getHeaderField("Content-Type");
            if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));
            String requestId = connection.getHeaderField("X-Request-Id");
            if (requestId != null) headers.set("X-Request-Id", requestId);
            InputStream responseBody = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseText = responseBody == null ? "" : new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
            return new ResponseEntity<>(responseText, headers, org.springframework.http.HttpStatusCode.valueOf(status));
        } finally {
            connection.disconnect();
        }
    }

    private void assertProblem(ResponseEntity<String> response, int status, String errorCode) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("type").asText()).isEqualTo("about:blank");
        assertThat(problem.path("status").asInt()).isEqualTo(status);
        assertThat(problem.path("title").asText()).isNotBlank();
        assertThat(problem.path("detail").asText()).isNotBlank();
        assertThat(problem.path("instance").asText()).isNotBlank();
        assertThat(problem.path("errorCode").asText())
                .withFailMessage("Expected errorCode %s in RFC 9457 body: %s", errorCode, response.getBody())
                .isEqualTo(errorCode);
        assertThat(problem.path("requestId").asText()).isNotBlank();
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(problem.path("requestId").asText());
    }

    private ResponseEntity<String> putPlace(Long placeId, UUID subject, String name, Long categoryId) {
        HttpHeaders headers = authHeaders(subject);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url("/api/places/" + placeId), HttpMethod.PUT,
                new HttpEntity<>(placeRequest(name, categoryId), headers), String.class);
    }

    private ResponseEntity<String> postPlace(UUID subject, String spoofedCoupleId, String name, Long categoryId) {
        HttpHeaders headers = authHeaders(subject);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Couple-Id", spoofedCoupleId);
        return http.exchange(url("/api/places"), HttpMethod.POST,
                new HttpEntity<>(placeRequest(name, categoryId), headers), String.class);
    }

    private static String placeRequest(String name, Long categoryId) {
        return """
                {"name":"%s","address":"","sourceUrl":null,"mapsUrl":null,
                 "acceptsReservations":false,"categoryId":%d,"tagIds":[]}
                """.formatted(name, categoryId);
    }

    private ResponseEntity<String> putReview(Long placeId, UUID subject, String comment) {
        HttpHeaders headers = authHeaders(subject);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"comment":"%s","location":4,"heating":null,"bathrooms":null,
                 "exterior":null,"seating":null,"service":null,"ambiance":null}
                """.formatted(comment);
        return http.exchange(url("/api/places/" + placeId + "/review"), HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> getPhoto(Long placeId, UUID subject) {
        return http.exchange(url("/api/places/" + placeId + "/photo"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(subject)), byte[].class);
    }

    private HttpHeaders authHeaders(UUID subject) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken(subject));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Fixture seedFixture() throws Exception {
        try (Connection connection = adminConnection()) {
            long categoryId = insertCategory(connection);
            long userA1 = insertUser(connection, "http-user-a1", USER_A1_AUTH_ID, "USER");
            long userA2 = insertUser(connection, "http-user-a2", USER_A2_AUTH_ID, "USER");
            long userB = insertUser(connection, "http-user-b", USER_B_AUTH_ID, "USER");
            insertUser(connection, "http-admin", ADMIN_AUTH_ID, "ADMIN");
            insertUser(connection, "http-orphan", ORPHAN_AUTH_ID, "USER");
            insertCouple(connection, COUPLE_A_ID, userA1, "ACTIVE");
            insertCouple(connection, COUPLE_B_ID, userB, "PENDING");
            insertMember(connection, COUPLE_A_ID, userA1, "Member A1", 1);
            insertMember(connection, COUPLE_A_ID, userA2, "Member A2", 2);
            insertMember(connection, COUPLE_B_ID, userB, "Member B", 1);
            long placeA = insertPlace(connection, "Private place A", categoryId, userA1, COUPLE_A_ID);
            long placeB = insertPlace(connection, "Private place B", categoryId, userB, COUPLE_B_ID);
            LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
            insertSpecialDateAndVisit(connection, placeA, userA1, COUPLE_A_ID, "Private anniversary A", today.minusYears(1), "ANNUAL", today);
            insertSpecialDateAndVisit(connection, placeB, userB, COUPLE_B_ID, "Private anniversary B", today.minusMonths(1), "MONTHLY", today);
            long archivedPlaceB = insertPlace(connection, "Archived private place B", categoryId, userB, COUPLE_B_ID);
            try (PreparedStatement archive = connection.prepareStatement(
                    "update places set deactivated_at = now() where id = ?")) {
                archive.setLong(1, archivedPlaceB);
                archive.executeUpdate();
            }
            insertPhoto(connection, placeB, COUPLE_B_ID);
            insertReview(connection, placeA, userA1, COUPLE_A_ID, "Review from member one");
            long recipeA = insertRecipe(connection, "Torta pareja A", userA1, COUPLE_A_ID);
            long recipeB = insertRecipe(connection, "Torta pareja B", userB, COUPLE_B_ID);
            long cookingA = insertCooking(connection, recipeA, userA1, COUPLE_A_ID, "TOMAS");
            long cookingB = insertCooking(connection, recipeB, userB, COUPLE_B_ID, "AVRIL");
            insertCookingReview(connection, cookingA, userA1, COUPLE_A_ID, 5);
            insertCookingReview(connection, cookingB, userB, COUPLE_B_ID, 1);
            long filmA = insertFilm(connection, "Private film A", userA1, COUPLE_A_ID);
            long filmB = insertFilm(connection, "Private film B", userB, COUPLE_B_ID);
            long dramaGenre = scalarLong(connection, "select id from film_genre_options where lower(name) = 'drama' limit 1");
            insertFilmGenre(connection, filmA, dramaGenre, COUPLE_A_ID);
            insertFilmGenre(connection, filmB, dramaGenre, COUPLE_B_ID);
            long activityCategoryId = insertActivityCategory(connection, "HTTP Activity Test", "http-activity-test", null);
            long activitySubcategoryId = insertActivityCategory(connection, "HTTP Activity Subtest", "http-activity-subtest", activityCategoryId);
            long activityA = insertActivity(connection, "Museo pareja A", activityCategoryId, activitySubcategoryId, userA1, COUPLE_A_ID);
            long activityB = insertActivity(connection, "Museo pareja B", activityCategoryId, activitySubcategoryId, userB, COUPLE_B_ID);
            long activityVisitA = insertActivityVisit(connection, activityA, userA1, COUPLE_A_ID);
            long activityVisitB = insertActivityVisit(connection, activityB, userB, COUPLE_B_ID);
            insertActivityReview(connection, activityVisitA, userA1, COUPLE_A_ID, 5);
            insertActivityReview(connection, activityVisitB, userB, COUPLE_B_ID, 1);
            return new Fixture(placeA, placeB, categoryId, activityCategoryId, activitySubcategoryId);
        }
    }

    private static long insertFilm(Connection connection, String title, long authorId, UUID coupleId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into films(title, created_by, updated_by, couple_id)
                values (?, ?, ?, ?) returning id
                """)) {
            statement.setString(1, title);
            statement.setLong(2, authorId);
            statement.setLong(3, authorId);
            statement.setObject(4, coupleId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static void insertSpecialDateAndVisit(Connection connection, long placeId, long userId, UUID coupleId,
            String label, LocalDate specialDateOn, String recurrence, LocalDate visitedOn) throws Exception {
        try (PreparedStatement dateStatement = connection.prepareStatement("""
                insert into special_dates(special_date, label, recurrence, couple_id)
                values (?, ?, ?, ?) returning id
                """)) {
            dateStatement.setObject(1, specialDateOn); dateStatement.setString(2, label); dateStatement.setString(3, recurrence); dateStatement.setObject(4, coupleId);
            try (ResultSet result = dateStatement.executeQuery()) {
                result.next();
            }
        }
        try (PreparedStatement visit = connection.prepareStatement("""
                insert into place_visits(place_id, visited_on, created_by, updated_by, couple_id)
                values (?, ?, ?, ?, ?)
                """)) {
            visit.setLong(1, placeId); visit.setObject(2, visitedOn); visit.setLong(3, userId); visit.setLong(4, userId);
            visit.setObject(5, coupleId); visit.executeUpdate();
        }
    }

    private static long scalarLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new IllegalStateException("Expected a seeded lookup row");
            return result.getLong(1);
        }
    }

    private static void insertFilmGenre(Connection connection, long filmId, long genreId, UUID coupleId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into film_genres(film_id, genre_id, couple_id) values (?, ?, ?)")) {
            statement.setLong(1, filmId); statement.setLong(2, genreId); statement.setObject(3, coupleId);
            statement.executeUpdate();
        }
    }

    private static long insertActivityCategory(Connection connection, String name, String slug, Long parentId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into why_fun_categories(parent_id, name, slug, icon, active)
                values (?, ?, ?, 'T', true) returning id
                """)) {
            if (parentId == null) statement.setNull(1, java.sql.Types.BIGINT);
            else statement.setLong(1, parentId);
            statement.setString(2, name);
            statement.setString(3, slug);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertActivity(Connection connection, String name, long categoryId, long subcategoryId,
            long authorId, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into why_fun_venues(name, address, category_id, subcategory_id, created_by, updated_by, couple_id)
                values (?, 'Centro', ?, ?, ?, ?, ?) returning id
                """)) {
            statement.setString(1, name);
            statement.setLong(2, categoryId);
            statement.setLong(3, subcategoryId);
            statement.setLong(4, authorId);
            statement.setLong(5, authorId);
            statement.setObject(6, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertActivityVisit(Connection connection, long activityId, long authorId, UUID coupleId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into why_fun_visits(venue_id, created_by, updated_by, couple_id)
                values (?, ?, ?, ?) returning id
                """)) {
            statement.setLong(1, activityId);
            statement.setLong(2, authorId);
            statement.setLong(3, authorId);
            statement.setObject(4, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertActivityReview(Connection connection, long visitId, long authorId, UUID coupleId,
            int rating) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into why_fun_visit_reviews(visit_id, author_id, updated_by, rating, couple_id)
                values (?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, visitId);
            statement.setLong(2, authorId);
            statement.setLong(3, authorId);
            statement.setInt(4, rating);
            statement.setObject(5, coupleId);
            statement.executeUpdate();
        }
    }

    private static long insertRecipe(Connection connection, String name, long authorId, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into recipes(name, created_by, updated_by, couple_id)
                values (?, ?, ?, ?) returning id
                """)) {
            statement.setString(1, name);
            statement.setLong(2, authorId);
            statement.setLong(3, authorId);
            statement.setObject(4, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertCooking(Connection connection, long recipeId, long authorId, UUID coupleId, String home)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into cookings(recipe_id, home, servings, cooked_on, meal_type, created_by, updated_by, couple_id)
                values (?, ?, 2, date '2026-09-01', 'CENA', ?, ?, ?) returning id
                """)) {
            statement.setLong(1, recipeId);
            statement.setString(2, home);
            statement.setLong(3, authorId);
            statement.setLong(4, authorId);
            statement.setObject(5, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertCookingReview(Connection connection, long cookingId, long authorId, UUID coupleId,
            int rating) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into cooking_reviews(cooking_id, author_id, updated_by, rating, complexity, taste, couple_id)
                values (?, ?, ?, ?, 3, 4, ?)
                """)) {
            statement.setLong(1, cookingId);
            statement.setLong(2, authorId);
            statement.setLong(3, authorId);
            statement.setInt(4, rating);
            statement.setObject(5, coupleId);
            statement.executeUpdate();
        }
    }

    private static long insertCategory(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into categories(name, slug, icon, active) values ('HTTP isolation test', 'http-isolation-test', 'T', true)
                on conflict (slug) do update set active = true returning id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertUser(Connection connection, String username, UUID authId, String role) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into users(username, role, auth_user_id) values (?, ?, ?) returning id")) {
            statement.setString(1, username);
            statement.setString(2, role);
            statement.setObject(3, authId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertCouple(Connection connection, UUID coupleId, long createdBy, String status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into couples(id, status, created_by) values (?, ?, ?)")) {
            statement.setObject(1, coupleId);
            statement.setString(2, status);
            statement.setLong(3, createdBy);
            statement.executeUpdate();
        }
    }

    private static void insertMember(Connection connection, UUID coupleId, long userId, String name, int slot)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into couple_members(couple_id, user_id, display_name, slot, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setObject(1, coupleId);
            statement.setLong(2, userId);
            statement.setString(3, name);
            statement.setInt(4, slot);
            statement.executeUpdate();
        }
    }

    private static long insertPlace(Connection connection, String name, long categoryId, long authorId, UUID coupleId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into places(name, category_id, created_by, updated_by, couple_id)
                values (?, ?, ?, ?, ?) returning id
                """)) {
            statement.setString(1, name);
            statement.setLong(2, categoryId);
            statement.setLong(3, authorId);
            statement.setLong(4, authorId);
            statement.setObject(5, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertPhoto(Connection connection, long placeId, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into place_photos(place_id, image_base64, thumbnail_base64, width, height, couple_id)
                values (?, ?, ?, 1, 1, ?)
                """)) {
            statement.setLong(1, placeId);
            statement.setString(2, Base64.getEncoder().encodeToString("private-photo-b".getBytes(StandardCharsets.UTF_8)));
            statement.setString(3, Base64.getEncoder().encodeToString("private-thumbnail-b".getBytes(StandardCharsets.UTF_8)));
            statement.setObject(4, coupleId);
            statement.executeUpdate();
        }
    }

    private static void insertReview(Connection connection, long placeId, long authorId, UUID coupleId, String comment)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into place_reviews(place_id, author_id, comment, location, heating, bathrooms,
                  exterior, seating, service, ambiance, couple_id)
                values (?, ?, ?, 4, 4, 4, 4, 4, 4, 4, ?)
                """)) {
            statement.setLong(1, placeId);
            statement.setLong(2, authorId);
            statement.setString(3, comment);
            statement.setObject(4, coupleId);
            statement.executeUpdate();
        }
    }

    private void assertReviewIsolation(Fixture fixture) throws Exception {
        ResponseEntity<String> place = get("/api/places/" + fixture.placeA(), USER_A1_AUTH_ID, null);
        assertThat(place.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(place.getBody());
        JsonNode reviews = json.path("reviews");
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(place.getBody()).contains("Review from member one", "Review from member two")
                .doesNotContain("Cross-couple review");
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "select count(*), min(comment), max(comment) from place_reviews where place_id = ?")) {
            statement.setLong(1, fixture.placeA());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
                assertThat(result.getString(2)).isEqualTo("Review from member one");
                assertThat(result.getString(3)).isEqualTo("Review from member two");
            }
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String accessToken(UUID subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject.toString())
                .audience().add(AUDIENCE).and()
                .claim("token_type", "access")
                .issuedAt(Date.from(now.minusSeconds(5)))
                .notBefore(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(JWT_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair newRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize integration test signing key", exception);
        }
    }

    private static String publicPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(JWT_KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private record Fixture(Long placeA, Long placeB, Long categoryId, Long activityCategoryId,
            Long activitySubcategoryId) {}
}
