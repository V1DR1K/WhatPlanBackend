package com.wherefood.couple;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories;
import com.wherefood.web.PlaceMediaService;
import com.wherefood.web.WhenDateMutationService;
import com.wherefood.web.WhyFunMediaService;
import io.jsonwebtoken.Jwts;
import java.awt.image.BufferedImage;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
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

    @Autowired
    private CoupleService coupleService;

    @Autowired
    private PlaceMediaService placeMediaService;

    @Autowired
    private WhyFunMediaService whyFunMediaService;

    @Autowired
    private WhenDateMutationService whenDateMutationService;

    @Autowired
    private Repositories.Users users;

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
        properties.add("TRUSTED_PROXY_ADDRESSES", () -> "127.0.0.1");
        properties.add("AUTH_COOKIE_SECURE", () -> "true");
        properties.add("AUTH_REFRESH_COOKIE_TTL_SECONDS", () -> "604800");
        properties.add("TMDB_READ_ACCESS_TOKEN", () -> "test-only-tmdb-token");
    }

    @Test
    void migrationSeedsInitialZonesAndAssignsExistingCatalogRowsToRosario() throws Exception {
        try (Connection connection = adminConnection();
                PreparedStatement zones = connection.prepareStatement(
                        "select name from zones where active order by id");
                ResultSet result = zones.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Rosario");
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Buenos Aires");
            assertThat(result.next()).isFalse();
        }
        try (Connection connection = adminConnection();
                PreparedStatement rows = connection.prepareStatement("""
                        select (select count(*) from places where zone_id is null) +
                               (select count(*) from films where zone_id is null) +
                               (select count(*) from recipes where zone_id is null) +
                               (select count(*) from why_fun_venues where zone_id is null)
                        """);
                ResultSet result = rows.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
    }

    @Test
    void productionHttpChainKeepsReadsWritesReviewsAndPhotosInsideTheAuthenticatedCouple() throws Exception {
        assertRuntimeDatabaseRoleIsRestricted();
        Fixture fixture = seedFixture();
        assertAdminMemberCanReadOwnCityCatalogs(fixture);
        benchmarkCalendarSummaryQuery(fixture);

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
        assertProblem(http.getForEntity(url("/api/actuator/prometheus"), String.class), 401, "UNAUTHORIZED");
        assertProblem(get("/api/actuator/prometheus", USER_A1_AUTH_ID, null), 403, "FORBIDDEN");
        ResponseEntity<String> adminMetrics = get("/api/actuator/prometheus", ADMIN_AUTH_ID, null);
        assertThat(adminMetrics.getStatusCode().value()).isEqualTo(200);
        assertThat(adminMetrics.getBody()).contains("# HELP", "whatplan_media_uploads_total")
                .doesNotContain(COUPLE_A_ID.toString(), COUPLE_B_ID.toString(), "couple_id");

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
        assertCoupleScopedDetail("/api/how-cook/recipes/", fixture.recipeA(), fixture.recipeB(), "Torta pareja A");
        assertCoupleScopedDetail("/api/how-cook/cookings/", fixture.cookingA(), fixture.cookingB(), "Torta pareja A");

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
        assertCoupleScopedDetail("/api/why-fun/activities/", fixture.activityA(), fixture.activityB(), "Museo pareja A");
        assertCoupleScopedDetail("/api/why-fun/activity-visits/", fixture.activityVisitA(), fixture.activityVisitB(), "Museo pareja A");

        long previousActivityVisitA;
        long newestActivityVisitA;
        try (Connection connection = adminConnection()) {
            long author = scalarLong(connection, "select id from users where username = 'http-user-a1'");
            previousActivityVisitA = insertActivityVisit(connection, fixture.activityA(), author, COUPLE_A_ID);
            newestActivityVisitA = insertActivityVisit(connection, fixture.activityA(), author, COUPLE_A_ID);
        }
        String activityVisitsPath = "/api/why-fun/activities/" + fixture.activityA() + "/visits";
        ResponseEntity<String> firstActivityVisitPage = get(activityVisitsPath + "?size=1", USER_A1_AUTH_ID, null);
        JsonNode firstActivityVisitPageBody = objectMapper.readTree(firstActivityVisitPage.getBody());
        assertThat(firstActivityVisitPage.getStatusCode().value()).isEqualTo(200);
        assertThat(firstActivityVisitPageBody.path("content").get(0).path("id").asLong())
                .isEqualTo(newestActivityVisitA);
        String activityVisitsCursor = firstActivityVisitPageBody.path("nextCursor").asText();
        assertThat(activityVisitsCursor).isNotBlank();
        ResponseEntity<String> secondActivityVisitPage = get(activityVisitsPath + "?size=1&cursor="
                + activityVisitsCursor, USER_A1_AUTH_ID, null);
        JsonNode secondActivityVisitPageBody = objectMapper.readTree(secondActivityVisitPage.getBody());
        assertThat(secondActivityVisitPageBody.path("content").get(0).path("id").asLong())
                .isEqualTo(previousActivityVisitA);
        assertThat(secondActivityVisitPageBody.path("content").get(0).path("activity").path("name").asText())
                .isEqualTo("Museo pareja A");
        String secondCursor = secondActivityVisitPageBody.path("nextCursor").asText();
        ResponseEntity<String> finalActivityVisitPage = get(activityVisitsPath + "?size=1&cursor="
                + secondCursor, USER_A1_AUTH_ID, null);
        JsonNode finalActivityVisitPageBody = objectMapper.readTree(finalActivityVisitPage.getBody());
        assertThat(finalActivityVisitPageBody.path("content").get(0).path("id").asLong())
                .isEqualTo(fixture.activityVisitA());
        assertThat(finalActivityVisitPageBody.path("nextCursor").isNull()).isTrue();

        ResponseEntity<String> activityVisitsB = get("/api/why-fun/activities/" + fixture.activityB()
                + "/visits?size=1", USER_B_AUTH_ID, null);
        JsonNode activityVisitsBBody = objectMapper.readTree(activityVisitsB.getBody());
        assertThat(activityVisitsB.getStatusCode().value()).isEqualTo(200);
        assertThat(activityVisitsBBody.path("content").get(0).path("id").asLong())
                .isEqualTo(fixture.activityVisitB());
        assertThat(activityVisitsBBody.path("nextCursor").isNull()).isTrue();
        assertThat(activityVisitsB.getBody()).contains("Museo pareja B").doesNotContain("Museo pareja A");
        assertProblem(get(activityVisitsPath + "?cursor=not-a-cursor", USER_A1_AUTH_ID, null), 400, "INVALID_REQUEST");

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
        assertCoupleScopedDetail("/api/films/", fixture.filmA(), fixture.filmB(), "Private film A");
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
        assertThat(calendarForB.getBody()).contains("Private anniversary B")
                .doesNotContain("Private anniversary A", "Synthetic calendar event");
        assertCoupleScopedOccurrence(fixture);

        ResponseEntity<String> spoofedCoupleHeader = get("/api/places", USER_A1_AUTH_ID, COUPLE_B_ID.toString());
        assertThat(spoofedCoupleHeader.getStatusCode().value()).isEqualTo(200);
        assertThat(spoofedCoupleHeader.getBody()).contains("Private place A").doesNotContain("Private place B");

        assertThat(get("/api/places/" + fixture.placeA(), USER_A1_AUTH_ID, null).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> hiddenPlace = get("/api/places/" + fixture.placeB(), USER_A1_AUTH_ID, null);
        assertProblem(hiddenPlace, 404, "NOT_FOUND");
        assertThat(hiddenPlace.getBody()).doesNotContain("Private place B", "coupleId", "couple_id");
        assertThat(get("/api/places/" + fixture.placeA(), USER_B_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);
        assertCrossCoupleDeleteDenied("/api/places/" + fixture.placeB(), "/api/places/" + fixture.placeB(),
                USER_B_AUTH_ID, "Private place B");
        assertCrossCoupleDeleteDenied("/api/place-visits/" + fixture.placeVisitB(), "/api/place-visits/" + fixture.placeVisitB(),
                USER_B_AUTH_ID, "Private photo item B");
        assertProblem(delete("/api/items/" + fixture.itemB(), USER_A1_AUTH_ID), 404, "NOT_FOUND");
        assertThat(get("/api/items?placeId=" + fixture.placeB(), USER_B_AUTH_ID, null).getStatusCode().value()).isEqualTo(200);
        assertCrossCoupleDeleteDenied("/api/how-cook/recipes/" + fixture.recipeB(), "/api/how-cook/recipes/" + fixture.recipeB(),
                USER_B_AUTH_ID, "Torta pareja B");
        assertCrossCoupleDeleteDenied("/api/how-cook/cookings/" + fixture.cookingB(), "/api/how-cook/cookings/" + fixture.cookingB(),
                USER_B_AUTH_ID, "Torta pareja B");
        assertCrossCoupleDeleteDenied("/api/films/" + fixture.filmB(), "/api/films/" + fixture.filmB(),
                USER_B_AUTH_ID, "Private film B");
        assertCrossCoupleDeleteDenied("/api/why-fun/activities/" + fixture.activityB(), "/api/why-fun/activities/" + fixture.activityB(),
                USER_B_AUTH_ID, "Museo pareja B");
        assertCrossCoupleDeleteDenied("/api/why-fun/activity-visits/" + fixture.activityVisitB(),
                "/api/why-fun/activity-visits/" + fixture.activityVisitB(), USER_B_AUTH_ID, "Museo pareja B");
        assertProblem(delete("/api/when-dates/photos/" + fixture.occurrencePhotoB(), USER_A1_AUTH_ID), 404, "NOT_FOUND");
        assertThat(getPrivatePhoto("/api/when-dates/photos/" + fixture.occurrencePhotoB(), USER_B_AUTH_ID)
                .getStatusCode().value()).isEqualTo(200);
        assertCrossCouplePhotoDeleteDenied("/api/place-visit-photos/" + fixture.placeVisitPhotoB(),
                "/api/place-visit-photos/" + fixture.placeVisitPhotoB(), USER_B_AUTH_ID);
        assertCrossCouplePhotoDeleteDenied("/api/why-fun/activity-visit-photos/" + fixture.activityVisitPhotoB(),
                "/api/why-fun/activity-visit-photos/" + fixture.activityVisitPhotoB(), USER_B_AUTH_ID);
        assertCrossCouplePhotoDeleteDenied("/api/why-fun/photos/" + fixture.activityVenuePhotoB(),
                "/api/why-fun/photos/" + fixture.activityVenuePhotoB(), USER_B_AUTH_ID);
        assertThat(get("/api/places/" + fixture.placeB() + "/visits", USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);
        long historyPlaceId;
        try (Connection connection = adminConnection()) {
            long authorId = scalarLong(connection, "select id from users where username = 'http-user-a1'");
            historyPlaceId = insertPlace(connection, "Keyset history test place", fixture.categoryId(), authorId, COUPLE_A_ID);
        }
        LocalDate placeVisitPageDateOne = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(3);
        LocalDate placeVisitPageDateTwo = placeVisitPageDateOne.plusDays(1);
        long historyVisitId = insertPlaceVisitForHistoryTest(historyPlaceId, placeVisitPageDateOne, "http-user-a1", COUPLE_A_ID);
        insertPlaceVisitForHistoryTest(historyPlaceId, placeVisitPageDateTwo, "http-user-a1", COUPLE_A_ID);
        try (Connection connection = adminConnection()) {
            long authorId = scalarLong(connection, "select id from users where username = 'http-user-a1'");
            insertItem(connection, historyVisitId, authorId, COUPLE_A_ID);
        }
        assertThat(get("/api/places/" + historyPlaceId + "/visits", USER_B_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);
        JsonNode placeVisitPageOne = objectMapper.readTree(get("/api/places/" + historyPlaceId + "/visits?size=1",
                USER_A1_AUTH_ID, null).getBody());
        assertThat(placeVisitPageOne.path("content")).hasSize(1);
        assertThat(placeVisitPageOne.path("content").get(0).path("visitedOn").asText()).isEqualTo(placeVisitPageDateTwo.toString());
        String placeVisitCursor = placeVisitPageOne.path("nextCursor").asText();
        assertThat(placeVisitCursor).isNotBlank();
        JsonNode placeVisitPageTwo = objectMapper.readTree(get("/api/places/" + historyPlaceId
                + "/visits?size=1&cursor=" + placeVisitCursor, USER_A1_AUTH_ID, null).getBody());
        assertThat(placeVisitPageTwo.path("content")).hasSize(1);
        assertThat(placeVisitPageTwo.path("content").get(0).path("visitedOn").asText()).isEqualTo(placeVisitPageDateOne.toString());
        assertProblem(get("/api/places/" + historyPlaceId + "/visits?cursor=invalid", USER_A1_AUTH_ID, null),
                400, "INVALID_REQUEST");
        ResponseEntity<String> itemDatesA = get("/api/places/" + historyPlaceId + "/item-dates", USER_A1_AUTH_ID, null);
        assertThat(itemDatesA.getStatusCode().value()).isEqualTo(200);
        assertThat(itemDatesA.getBody()).contains(placeVisitPageDateOne.toString());
        assertThat(get("/api/places/" + fixture.placeB() + "/item-dates", USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/items?placeId=" + fixture.placeB(), USER_A1_AUTH_ID, null)
                .getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> attemptedCrossCoupleUpdate = putPlace(fixture.placeB(), USER_A1_AUTH_ID,
                "Attempted cross-couple edit", fixture.categoryId());
        assertThat(attemptedCrossCoupleUpdate.getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places/" + fixture.placeB(), USER_B_AUTH_ID, null).getBody())
                .contains("Private place B").doesNotContain("Attempted cross-couple edit");

        assertCrossCoupleUpdateDenied("/api/place-visits/" + fixture.placeVisitB(), USER_A1_AUTH_ID,
                "{\"visitedOn\":\"2026-09-01\"}", "/api/place-visits/" + fixture.placeVisitB(),
                USER_B_AUTH_ID, "Private photo item B");
        assertCrossCoupleUpdateDenied("/api/items/" + fixture.itemB(), USER_A1_AUTH_ID,
                "{\"name\":\"Attempted item edit\"}", "/api/items?placeId=" + fixture.placeB(),
                USER_B_AUTH_ID, "Private photo item B");
        assertCrossCoupleUpdateDenied("/api/how-cook/recipes/" + fixture.recipeB(), USER_A1_AUTH_ID,
                "{\"name\":\"Attempted recipe edit\",\"ingredients\":[],\"steps\":[]}",
                "/api/how-cook/recipes/" + fixture.recipeB(), USER_B_AUTH_ID, "Torta pareja B");
        assertCrossCoupleUpdateDenied("/api/how-cook/cookings/" + fixture.cookingB(), USER_A1_AUTH_ID,
                "{\"home\":\"TOMAS\",\"servings\":2,\"cookedOn\":\"2026-09-01\",\"mealType\":\"ALMUERZO\"}",
                "/api/how-cook/cookings/" + fixture.cookingB(), USER_B_AUTH_ID, "Torta pareja B");
        assertCrossCoupleUpdateDenied("/api/films/" + fixture.filmB(), USER_A1_AUTH_ID,
                "{\"title\":\"Attempted film edit\",\"genres\":[]}",
                "/api/films/" + fixture.filmB(), USER_B_AUTH_ID, "Private film B");
        assertCrossCoupleUpdateDenied("/api/why-fun/activities/" + fixture.activityB(), USER_A1_AUTH_ID,
                "{\"name\":\"Attempted activity edit\",\"address\":\"Private\",\"categoryId\":"
                        + fixture.activityCategoryId() + ",\"subcategoryId\":" + fixture.activitySubcategoryId() + ",\"schedules\":[]}",
                "/api/why-fun/activities/" + fixture.activityB(), USER_B_AUTH_ID, "Museo pareja B");
        assertCrossCoupleUpdateDenied("/api/why-fun/activity-visits/" + fixture.activityVisitB(), USER_A1_AUTH_ID,
                "{\"scheduledAt\":\"2026-09-01\"}", "/api/why-fun/activity-visits/" + fixture.activityVisitB(),
                USER_B_AUTH_ID, "Museo pareja B");
        assertCrossCoupleUpdateDenied("/api/special-dates/" + fixture.specialDateB(), USER_A1_AUTH_ID,
                "{\"date\":\"2026-09-01\",\"label\":\"Attempted date edit\",\"recurrence\":\"ANNUAL\"}",
                "/api/special-dates", USER_B_AUTH_ID, "Private anniversary B");
        assertProblem(jsonRequest("/api/when-dates/special-dates/" + fixture.specialDateB() + "/occurrences/"
                + fixture.occurrenceDate() + "/comments/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"comment\":\"Attempted occurrence comment\"}"), 404, "NOT_FOUND");
        String occurrencePath = "/api/when-dates/special-dates/" + fixture.specialDateA() + "/occurrences/"
                + fixture.occurrenceDate();
        assertThat(jsonRequest(occurrencePath + "/comments/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"comment\":\"Member A1 calendar note\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(jsonRequest(occurrencePath + "/comments/me", HttpMethod.PUT, USER_A2_AUTH_ID,
                "{\"comment\":\"Member A2 calendar note\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/when-dates?size=12", USER_B_AUTH_ID, null).getBody())
                .contains("Private anniversary B").doesNotContain("Attempted occurrence comment");
        assertThat(get(occurrencePath, USER_A1_AUTH_ID, null).getBody())
                .contains("Member A1 calendar note", "Member A2 calendar note");

        assertCrossCoupleCoverDenied("/api/place-visits/" + fixture.placeVisitA() + "/cover/"
                        + fixture.placeVisitPhotoB(),
                "/api/place-visits/" + fixture.placeVisitA(), USER_A1_AUTH_ID);
        assertCrossCoupleCoverDenied("/api/why-fun/activity-visits/" + fixture.activityVisitA() + "/cover/"
                        + fixture.activityVisitPhotoB(),
                "/api/why-fun/activity-visits/" + fixture.activityVisitA(), USER_A1_AUTH_ID);
        assertCrossCoupleCoverDenied("/api/when-dates/occurrences/" + fixture.occurrenceA() + "/cover/"
                        + fixture.occurrencePhotoB(),
                "/api/when-dates/special-dates/" + fixture.specialDateA() + "/occurrences/"
                        + fixture.occurrenceDate(), USER_A1_AUTH_ID);

        for (UploadAttempt upload : List.of(
                new UploadAttempt("/api/places/" + fixture.placeB() + "/photo", "place_photos"),
                new UploadAttempt("/api/items/" + fixture.itemB() + "/photo", "item_photos"),
                new UploadAttempt("/api/place-visits/" + fixture.placeVisitB() + "/photos", "place_visit_photos"),
                new UploadAttempt("/api/how-cook/recipes/" + fixture.recipeB() + "/photo", "recipe_photos"),
                new UploadAttempt("/api/films/" + fixture.filmB() + "/photo", "film_photos"),
                new UploadAttempt("/api/why-fun/activities/" + fixture.activityB() + "/photo", "why_fun_venue_photos"),
                new UploadAttempt("/api/why-fun/activity-visits/" + fixture.activityVisitB() + "/photos", "why_fun_visit_photos"),
                new UploadAttempt("/api/when-dates/special-dates/" + fixture.specialDateB() + "/occurrences/"
                        + fixture.occurrenceDate() + "/photos", "special_date_occurrence_photos"))) {
            long photosBefore = tableCount(upload.table());
            assertProblem(postPhoto(upload.path(), USER_A1_AUTH_ID), 404, "NOT_FOUND");
            assertThat(tableCount(upload.table())).as("no photo inserted for %s", upload.path()).isEqualTo(photosBefore);
        }

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

        assertCrossCoupleCreateDenied("/api/places/" + fixture.placeB() + "/visits", USER_A1_AUTH_ID,
                "{\"visitedOn\":\"2026-09-02\"}");
        assertCrossCoupleCreateDenied("/api/place-visits/" + fixture.placeVisitB() + "/items", USER_A1_AUTH_ID,
                "{\"name\":\"Attempted cross-couple item\"}");
        assertCrossCoupleCreateDenied("/api/how-cook/recipes/" + fixture.recipeB() + "/cookings", USER_A1_AUTH_ID,
                "{\"home\":\"TOMAS\",\"servings\":2,\"cookedOn\":\"2026-09-02\",\"mealType\":\"ALMUERZO\"}");
        assertCrossCoupleCreateDenied("/api/why-fun/activities/" + fixture.activityB() + "/visits", USER_A1_AUTH_ID,
                "{\"scheduledAt\":\"2026-09-02\"}");

        for (String photoPath : fixture.privatePhotoPaths()) {
            ResponseEntity<byte[]> crossCouplePhoto = getPrivatePhoto(photoPath, USER_A1_AUTH_ID);
            assertThat(crossCouplePhoto.getStatusCode().value()).as(photoPath).isEqualTo(404);

            ResponseEntity<byte[]> ownPhoto = getPrivatePhoto(photoPath, USER_B_AUTH_ID);
            assertThat(ownPhoto.getStatusCode().value()).as(photoPath).isEqualTo(200);
            assertThat(ownPhoto.getHeaders().getContentType()).as(photoPath).isEqualTo(MediaType.valueOf("image/webp"));
            assertThat(ownPhoto.getHeaders().getCacheControl()).as(photoPath).contains("no-store");
            assertThat(ownPhoto.getHeaders().getVary()).as(photoPath).contains("Authorization", "Cookie");
            assertThat(ownPhoto.getBody()).as(photoPath).isNotEmpty();
        }

        assertThat(putReview(fixture.placeA(), USER_A2_AUTH_ID, "Review from member two").getStatusCode().value())
                .isEqualTo(200);
        assertThat(putReview(fixture.placeA(), USER_A2_AUTH_ID, "Updated review from member two").getStatusCode().value())
                .isEqualTo(200);
        assertThat(get("/api/places/" + fixture.placeA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Review from member one", "Updated review from member two")
                .doesNotContain("Review from member two\"");
        assertThat(putReview(fixture.placeA(), USER_B_AUTH_ID, "Cross-couple review").getStatusCode().value())
                .isEqualTo(404);
        assertProblem(jsonRequest("/api/items/" + fixture.itemB() + "/reviews/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"comment\":\"Cross-couple review\",\"taste\":4,\"price\":4}"), 404, "NOT_FOUND");
        assertProblem(jsonRequest("/api/place-visits/" + fixture.placeVisitB() + "/reviews/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"overall\":4,\"comment\":\"Cross-couple review\",\"taste\":4,\"price\":4}"), 404, "NOT_FOUND");
        assertProblem(jsonRequest("/api/how-cook/cookings/" + fixture.cookingB() + "/reviews/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"rating\":4,\"complexity\":3,\"taste\":4,\"comment\":\"Cross-couple review\"}"), 404, "NOT_FOUND");
        assertProblem(jsonRequest("/api/films/" + fixture.filmB() + "/reviews", HttpMethod.POST, USER_A1_AUTH_ID,
                "{\"rating\":4,\"comment\":\"Cross-couple review\"}"), 404, "NOT_FOUND");
        assertProblem(jsonRequest("/api/why-fun/activity-visits/" + fixture.activityVisitB() + "/reviews/me",
                HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"rating\":4,\"comment\":\"Cross-couple review\"}"), 404, "NOT_FOUND");

        ResponseEntity<String> ownVisitReview = jsonRequest("/api/place-visits/" + fixture.placeVisitA() + "/reviews",
                HttpMethod.POST, USER_A1_AUTH_ID,
                "{\"overall\":5,\"comment\":\"Author-owned visit review\",\"taste\":5,\"price\":4}");
        assertThat(ownVisitReview.getStatusCode().value()).isEqualTo(201);
        assertThat(jsonRequest("/api/place-visits/" + fixture.placeVisitA() + "/reviews/me", HttpMethod.PUT,
                USER_A2_AUTH_ID,
                "{\"overall\":3,\"comment\":\"Member A2 visit review\",\"taste\":3,\"price\":2}")
                .getStatusCode().value()).isEqualTo(200);
        long visitReviewId = objectMapper.readTree(ownVisitReview.getBody()).path("id").asLong();
        assertThat(visitReviewId).isPositive();
        assertProblem(jsonRequest("/api/place-visit-reviews/" + visitReviewId, HttpMethod.PUT, USER_A2_AUTH_ID,
                "{\"overall\":1,\"comment\":\"Peer overwrite\",\"taste\":1,\"price\":1}"), 404, "NOT_FOUND");
        assertProblem(delete("/api/place-visit-reviews/" + visitReviewId, USER_A2_AUTH_ID), 404, "NOT_FOUND");

        ResponseEntity<String> ownFilmReview = jsonRequest("/api/films/" + fixture.filmA() + "/reviews",
                HttpMethod.POST, USER_A1_AUTH_ID,
                "{\"rating\":5,\"comment\":\"Author-owned film review\"}");
        assertThat(ownFilmReview.getStatusCode().value()).isEqualTo(200);
        long filmReviewId = objectMapper.readTree(ownFilmReview.getBody()).path("id").asLong();
        assertThat(filmReviewId).isPositive();
        assertProblem(jsonRequest("/api/films/" + fixture.filmA() + "/reviews/" + filmReviewId,
                HttpMethod.PUT, USER_A2_AUTH_ID,
                "{\"rating\":1,\"comment\":\"Peer overwrite\"}"), 404, "NOT_FOUND");
        assertProblem(delete("/api/films/" + fixture.filmA() + "/reviews/" + filmReviewId,
                USER_A2_AUTH_ID), 404, "NOT_FOUND");

        ResponseEntity<String> itemCreatedByA1 = jsonRequest("/api/place-visits/" + fixture.placeVisitA() + "/items",
                HttpMethod.POST, USER_A1_AUTH_ID, "{\"name\":\"Personal review item\"}");
        assertThat(itemCreatedByA1.getStatusCode().value()).isEqualTo(200);
        long itemA = objectMapper.readTree(itemCreatedByA1.getBody()).path("id").asLong();
        assertThat(jsonRequest("/api/items/" + itemA + "/reviews/me", HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"comment\":\"Member A1 item review\",\"taste\":5,\"price\":4}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jsonRequest("/api/items/" + itemA + "/reviews/me", HttpMethod.PUT, USER_A2_AUTH_ID,
                "{\"comment\":\"Member A2 item review\",\"taste\":2,\"price\":3}")
                .getStatusCode().value()).isEqualTo(200);

        assertThat(jsonRequest("/api/how-cook/cookings/" + fixture.cookingA() + "/reviews/me", HttpMethod.PUT,
                USER_A1_AUTH_ID,
                "{\"rating\":5,\"complexity\":3,\"taste\":5,\"comment\":\"Member A1 cooking review\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jsonRequest("/api/how-cook/cookings/" + fixture.cookingA() + "/reviews/me", HttpMethod.PUT,
                USER_A2_AUTH_ID,
                "{\"rating\":2,\"complexity\":4,\"taste\":2,\"comment\":\"Member A2 cooking review\"}")
                .getStatusCode().value()).isEqualTo(200);

        assertThat(jsonRequest("/api/why-fun/activity-visits/" + fixture.activityVisitA() + "/reviews/me",
                HttpMethod.PUT, USER_A1_AUTH_ID,
                "{\"rating\":5,\"comment\":\"Member A1 activity review\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jsonRequest("/api/why-fun/activity-visits/" + fixture.activityVisitA() + "/reviews/me",
                HttpMethod.PUT, USER_A2_AUTH_ID,
                "{\"rating\":2,\"comment\":\"Member A2 activity review\"}")
                .getStatusCode().value()).isEqualTo(200);

        assertThat(jsonRequest("/api/why-fun/plans/" + fixture.activityA() + "/review", HttpMethod.PUT,
                USER_A1_AUTH_ID, "{\"rating\":5,\"comment\":\"Member A1 plan review\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jsonRequest("/api/why-fun/plans/" + fixture.activityA() + "/review", HttpMethod.PUT,
                USER_A2_AUTH_ID, "{\"rating\":2,\"comment\":\"Member A2 plan review\"}")
                .getStatusCode().value()).isEqualTo(200);

        try (Connection connection = adminConnection()) {
            long cookingReviewId = scalarLong(connection, "select id from cooking_reviews where cooking_id = "
                    + fixture.cookingA() + " and author_id = (select id from users where username = 'http-user-a1')");
            long activityReviewId = scalarLong(connection, "select id from why_fun_visit_reviews where visit_id = "
                    + fixture.activityVisitA() + " and author_id = (select id from users where username = 'http-user-a1')");
            assertProblem(delete("/api/cooking-reviews/" + cookingReviewId, USER_A2_AUTH_ID), 404, "NOT_FOUND");
            assertProblem(delete("/api/why-fun/activity-visit-reviews/" + activityReviewId,
                    USER_A2_AUTH_ID), 404, "NOT_FOUND");
        }
        assertThat(get("/api/place-visits/" + fixture.placeVisitA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Author-owned visit review", "Member A2 visit review");
        assertThat(get("/api/place-visits/" + fixture.placeVisitA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Member A1 item review", "Member A2 item review");
        assertThat(get("/api/how-cook/cookings/" + fixture.cookingA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Member A1 cooking review", "Member A2 cooking review");
        assertThat(get("/api/why-fun/activity-visits/" + fixture.activityVisitA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Member A1 activity review", "Member A2 activity review");
        assertThat(get("/api/why-fun/plans/" + fixture.activityA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Member A1 plan review", "Member A2 plan review");
        assertThat(get("/api/films/" + fixture.filmA(), USER_A1_AUTH_ID, null).getBody())
                .contains("Author-owned film review");
        assertThat(get("/api/how-cook/cookings/" + fixture.cookingA(), USER_A1_AUTH_ID, null).getBody())
                .contains("\"rating\":5");
        assertThat(get("/api/why-fun/activity-visits/" + fixture.activityVisitA(), USER_A1_AUTH_ID, null).getBody())
                .contains("\"rating\":5");
        assertThat(get("/api/items?placeId=" + fixture.placeB(), USER_B_AUTH_ID, null).getBody())
                .doesNotContain("Cross-couple review");
        assertThat(get("/api/place-visits/" + fixture.placeVisitB(), USER_B_AUTH_ID, null).getBody())
                .doesNotContain("Cross-couple review");
        assertThat(get("/api/how-cook/cookings/" + fixture.cookingB(), USER_B_AUTH_ID, null).getBody())
                .contains("\"rating\":1").doesNotContain("Cross-couple review");
        assertThat(get("/api/films/" + fixture.filmB(), USER_B_AUTH_ID, null).getBody())
                .doesNotContain("Cross-couple review");
        assertThat(get("/api/why-fun/activity-visits/" + fixture.activityVisitB(), USER_B_AUTH_ID, null).getBody())
                .contains("\"rating\":1").doesNotContain("Cross-couple review");
        assertReviewIsolation(fixture);

        assertThat(get("/api/places", ADMIN_AUTH_ID, null).getBody()).doesNotContain("Private place A", "Private place B");
        assertThat(get("/api/places/" + fixture.placeA(), ADMIN_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/places", ORPHAN_AUTH_ID, null).getBody()).doesNotContain("Private place A", "Private place B");
        assertThat(get("/api/places/" + fixture.placeA(), ORPHAN_AUTH_ID, null).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> leaveCouple = http.postForEntity(url("/api/couple/leave"),
                new HttpEntity<>(authHeaders(USER_B_AUTH_ID)), String.class);
        assertThat(leaveCouple.getStatusCode().value()).isEqualTo(204);
        for (String photoPath : fixture.privatePhotoPaths()) {
            assertThat(getPrivatePhoto(photoPath, USER_B_AUTH_ID).getStatusCode().value()).as(photoPath).isEqualTo(404);
        }

        REDIS.stop();
        assertProblem(http.postForEntity(url("/api/auth/login"),
                new HttpEntity<>("{}", loginHeaders), String.class), 503, "RATE_LIMIT_UNAVAILABLE");
    }

    @Test
    void invitationCreationRechecksMembershipAfterWaitingForCoupleLock() throws Exception {
        InvitationRaceFixture fixture = createAcceptedPair("invite-create-race");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection admin = adminConnection()) {
            admin.setAutoCommit(false);
            lockCouple(admin, fixture.coupleId());
            CompletableFuture<CoupleService.InvitationSnapshot> staleCreate = CompletableFuture.supplyAsync(() -> {
                CoupleContext.set(fixture.coupleId());
                try {
                    return coupleService.createInvitation(fixture.owner());
                } finally {
                    CoupleContext.clear();
                }
            }, executor);
            awaitCoupleLockWaiter(admin);
            markMemberLeft(admin, fixture.coupleId(), fixture.owner().id);
            admin.commit();

            ExecutionException failed = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> staleCreate.get(10, TimeUnit.SECONDS));
            assertThat(failed.getCause()).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) failed.getCause()).getStatusCode().value()).isEqualTo(404);
            try (Connection verification = adminConnection()) {
                assertThat(countPendingInvitations(verification, fixture.coupleId())).isZero();
            }
        } finally {
            executor.shutdownNow();
            CoupleContext.clear();
        }
    }

    @Test
    void invitationRevocationRechecksMembershipAfterWaitingForCoupleLock() throws Exception {
        InvitationRaceFixture fixture = createAcceptedPair("invite-revoke-race");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection admin = adminConnection()) {
            admin.setAutoCommit(false);
            lockCouple(admin, fixture.coupleId());
            CompletableFuture<Void> staleRevoke = CompletableFuture.runAsync(() -> {
                CoupleContext.set(fixture.coupleId());
                try {
                    coupleService.revoke(fixture.invitationId(), fixture.owner());
                } finally {
                    CoupleContext.clear();
                }
            }, executor);
            awaitCoupleLockWaiter(admin);
            markMemberLeft(admin, fixture.coupleId(), fixture.owner().id);
            admin.commit();

            ExecutionException failed = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> staleRevoke.get(10, TimeUnit.SECONDS));
            assertThat(failed.getCause()).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) failed.getCause()).getStatusCode().value()).isEqualTo(404);
            assertThat(invitationStatus(fixture.invitationId())).isEqualTo("ACCEPTED");
        } finally {
            executor.shutdownNow();
            CoupleContext.clear();
        }
    }

    @Test
    void invitationAcceptanceRechecksRevocationAfterWaitingForCoupleLock() throws Exception {
        PendingInvitationFixture fixture = createPendingInvitation("invite-accept-race");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection admin = adminConnection()) {
            admin.setAutoCommit(false);
            lockCouple(admin, fixture.coupleId());
            CompletableFuture<CoupleService.CoupleSnapshot> staleAccept = CompletableFuture.supplyAsync(() -> {
                CoupleContext.set(fixture.coupleId());
                try {
                    return coupleService.accept(fixture.token(), fixture.invitee());
                } finally {
                    CoupleContext.clear();
                }
            }, executor);
            awaitCoupleLockWaiter(admin);
            revokeInvitation(admin, fixture.invitationId());
            admin.commit();

            ExecutionException failed = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                    () -> staleAccept.get(10, TimeUnit.SECONDS));
            assertThat(failed.getCause()).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) failed.getCause()).getStatusCode().value()).isEqualTo(404);
            assertThat(invitationStatus(fixture.invitationId())).isEqualTo("REVOKED");
            assertThat(activeMembershipCount(fixture.invitee().id)).isZero();
        } finally {
            executor.shutdownNow();
            CoupleContext.clear();
        }
    }

    @Test
    void concurrentInvitationAcceptanceAllowsOnlyOneInvitee() throws Exception {
        PendingInvitationFixture fixture = createPendingInvitation("invite-double-accept-race");
        User secondInvitee = createTestUser("invite-double-accept-race-second");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            CompletableFuture<CoupleService.CoupleSnapshot> first = acceptAsync(executor, fixture, fixture.invitee(), start);
            CompletableFuture<CoupleService.CoupleSnapshot> second = acceptAsync(executor, fixture, secondInvitee, start);
            int accepted = 0;
            for (CompletableFuture<CoupleService.CoupleSnapshot> attempt : List.of(first, second)) {
                try {
                    attempt.get(20, TimeUnit.SECONDS);
                    accepted++;
                } catch (ExecutionException rejected) {
                    assertThat(rejected.getCause()).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) rejected.getCause()).getStatusCode().value()).isEqualTo(404);
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(invitationStatus(fixture.invitationId())).isEqualTo("ACCEPTED");
            assertThat(activeMemberCount(fixture.coupleId())).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            CoupleContext.clear();
        }
    }

    @Test
    void concurrentGalleryUploadsRespectPerAggregatePhotoLimits() throws Exception {
        InvitationRaceFixture pair = createAcceptedPair("media-gallery-limit-race");
        User owner = pair.owner();
        UUID coupleId = pair.coupleId();
        long placeVisitId;
        long activityId;
        long activityVisitId;
        long specialDateId;
        LocalDate occurrenceDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

        try (Connection connection = adminConnection()) {
            long placeCategoryId = insertCategory(connection);
            long placeId = insertPlace(connection, "Gallery quota place", placeCategoryId, owner.id, coupleId);
            placeVisitId = insertSpecialDateAndVisit(connection, placeId, owner.id, coupleId,
                    "Gallery quota place visit", occurrenceDate, "ONCE", occurrenceDate);
            for (int index = 0; index < 3; index++) {
                insertGalleryPhoto(connection, "place_visit_photos", "visit_id", placeVisitId,
                        owner.id, coupleId, index);
            }

            String suffix = coupleId.toString().replace("-", "");
            long categoryId = insertActivityCategory(connection, "Gallery quota " + suffix,
                    "gallery-quota-" + suffix, null);
            long subcategoryId = insertActivityCategory(connection, "Gallery quota sub " + suffix,
                    "gallery-quota-sub-" + suffix, categoryId);
            activityId = insertActivity(connection, "Gallery quota activity", categoryId,
                    subcategoryId, owner.id, coupleId);
            for (int index = 0; index < 11; index++) {
                insertSinglePhoto(connection, "why_fun_venue_photos", "venue_id", activityId, coupleId);
            }
            activityVisitId = insertActivityVisit(connection, activityId, owner.id, coupleId);
            for (int index = 0; index < 3; index++) {
                insertGalleryPhoto(connection, "why_fun_visit_photos", "visit_id", activityVisitId,
                        owner.id, coupleId, index);
            }

            try (PreparedStatement specialDate = connection.prepareStatement("""
                    insert into special_dates(special_date, label, recurrence, couple_id)
                    values (?, ?, 'ONCE', ?) returning id
                    """)) {
                specialDate.setObject(1, occurrenceDate);
                specialDate.setString(2, "Gallery quota occurrence " + suffix);
                specialDate.setObject(3, coupleId);
                try (ResultSet result = specialDate.executeQuery()) {
                    result.next();
                    specialDateId = result.getLong(1);
                }
            }
            long occurrenceId = insertOccurrence(connection, specialDateId, occurrenceDate, owner.id, coupleId);
            for (int index = 0; index < 3; index++) {
                insertGalleryPhoto(connection, "special_date_occurrence_photos", "occurrence_id",
                        occurrenceId, owner.id, coupleId, index);
            }
        }

        assertConcurrentGalleryLimit(coupleId, owner, "place_visits", placeVisitId,
                "place_visit_photos", "visit_id", placeVisitId, 4,
                file -> placeMediaService.uploadVisitPhoto(placeVisitId, file, owner));
        assertConcurrentGalleryLimit(coupleId, owner, "why_fun_visits", activityVisitId,
                "why_fun_visit_photos", "visit_id", activityVisitId, 4,
                file -> whyFunMediaService.uploadVisitPhoto(activityVisitId, file, owner));
        long occurrenceId = occurrenceIdFor(specialDateId, occurrenceDate, coupleId);
        assertConcurrentGalleryLimit(coupleId, owner, "special_dates", specialDateId,
                "special_date_occurrence_photos", "occurrence_id", occurrenceId, 4,
                file -> whenDateMutationService.uploadPhoto(specialDateId, occurrenceDate, file, owner));
        assertConcurrentGalleryLimit(coupleId, owner, "why_fun_venues", activityId,
                "why_fun_venue_photos", "venue_id", activityId, 12,
                file -> whyFunMediaService.uploadPlanPhoto(activityId, file, owner));
    }

    private void assertConcurrentGalleryLimit(UUID coupleId, User owner, String parentTable, long parentId,
            String photoTable, String photoParentColumn, long photoParentId, int expectedPhotoCount,
            GalleryUpload upload) throws Exception {
        byte[] png = tinyPng();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try (Connection blocker = adminConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "select id from " + parentTable + " where id = ? for update")) {
                lock.setLong(1, parentId);
                try (ResultSet result = lock.executeQuery()) {
                    assertThat(result.next()).isTrue();
                }
            }

            CompletableFuture<Integer> first = uploadAsync(executor, start, coupleId, png, upload);
            CompletableFuture<Integer> second = uploadAsync(executor, start, coupleId, png, upload);
            awaitDatabaseLockWaiter(blocker);
            blocker.commit();

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            try (Connection verification = adminConnection(); PreparedStatement count = verification.prepareStatement(
                    "select count(*) from " + photoTable + " where " + photoParentColumn + " = ? and couple_id = ?")) {
                count.setLong(1, photoParentId);
                count.setObject(2, coupleId);
                try (ResultSet result = count.executeQuery()) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(expectedPhotoCount);
                }
            }
        } finally {
            executor.shutdownNow();
            CoupleContext.clear();
        }
    }

    private CompletableFuture<Integer> uploadAsync(ExecutorService executor, CyclicBarrier start,
            UUID coupleId, byte[] png, GalleryUpload upload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                CoupleContext.set(coupleId);
                upload.upload(new MockMultipartFile("file", "tiny.png", "image/png", png));
                return 200;
            } catch (ResponseStatusException rejected) {
                return rejected.getStatusCode().value();
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            } finally {
                CoupleContext.clear();
            }
        }, executor);
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "png", encoded);
        return encoded.toByteArray();
    }

    private long occurrenceIdFor(long specialDateId, LocalDate date, UUID coupleId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement occurrence = connection.prepareStatement(
                "select id from special_date_occurrences where special_date_id = ? and occurred_on = ? and couple_id = ?")) {
            occurrence.setLong(1, specialDateId);
            occurrence.setObject(2, date);
            occurrence.setObject(3, coupleId);
            try (ResultSet result = occurrence.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private static void awaitDatabaseLockWaiter(Connection blocker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int blockerPid;
        try (PreparedStatement statement = blocker.prepareStatement("select pg_backend_pid()");
                ResultSet result = statement.executeQuery()) {
            result.next();
            blockerPid = result.getInt(1);
        }
        while (System.nanoTime() < deadline) {
            try (PreparedStatement statement = blocker.prepareStatement(
                    "select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))")) {
                statement.setInt(1, blockerPid);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) > 0) return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Media mutation did not wait for the aggregate row lock");
    }

    private CompletableFuture<CoupleService.CoupleSnapshot> acceptAsync(
            ExecutorService executor, PendingInvitationFixture fixture, User invitee, CyclicBarrier start) {
        return CompletableFuture.supplyAsync(() -> {
            CoupleContext.set(fixture.coupleId());
            try {
                start.await(10, TimeUnit.SECONDS);
                return coupleService.accept(fixture.token(), invitee);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            } finally {
                CoupleContext.clear();
            }
        }, executor);
    }

    private ResponseEntity<String> get(String path, UUID subject, String spoofedCoupleId) {
        HttpHeaders headers = authHeaders(subject);
        if (spoofedCoupleId != null) headers.set("X-Couple-Id", spoofedCoupleId);
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> delete(String path, UUID subject) {
        return http.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(authHeaders(subject)), String.class);
    }

    private ResponseEntity<String> jsonRequest(String path, HttpMethod method, UUID subject, String body) {
        HttpHeaders headers = authHeaders(subject);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> putWithoutBody(String path, UUID subject) {
        return http.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(authHeaders(subject)), String.class);
    }

    private void assertCrossCoupleDeleteDenied(String deletePath, String ownerReadPath, UUID owner, String marker) throws Exception {
        assertProblem(delete(deletePath, USER_A1_AUTH_ID), 404, "NOT_FOUND");
        ResponseEntity<String> ownerRead = get(ownerReadPath, owner, null);
        assertThat(ownerRead.getStatusCode().value()).as(ownerReadPath).isEqualTo(200);
        assertThat(ownerRead.getBody()).as(ownerReadPath).contains(marker);
    }

    private void assertCrossCoupleUpdateDenied(String path, UUID attacker, String body,
            String ownerReadPath, UUID owner, String originalMarker) throws Exception {
        assertProblem(jsonRequest(path, HttpMethod.PUT, attacker, body), 404, "NOT_FOUND");
        ResponseEntity<String> ownerRead = get(ownerReadPath, owner, null);
        assertThat(ownerRead.getStatusCode().value()).as(ownerReadPath).isEqualTo(200);
        assertThat(ownerRead.getBody()).as(ownerReadPath).contains(originalMarker)
                .doesNotContain("Attempted");
    }

    private void assertCrossCouplePhotoDeleteDenied(String deletePath, String readPath, UUID owner) throws Exception {
        assertProblem(delete(deletePath, USER_A1_AUTH_ID), 404, "NOT_FOUND");
        ResponseEntity<byte[]> ownerRead = getPrivatePhoto(readPath, owner);
        assertThat(ownerRead.getStatusCode().value()).as(readPath).isEqualTo(200);
        assertThat(ownerRead.getBody()).as(readPath).isNotEmpty();
    }

    private void assertCrossCoupleCreateDenied(String path, UUID attacker, String body) throws Exception {
        assertProblem(jsonRequest(path, HttpMethod.POST, attacker, body), 404, "NOT_FOUND");
    }

    private void assertCrossCoupleCoverDenied(String updatePath, String readPath, UUID attacker) throws Exception {
        ResponseEntity<String> before = get(readPath, attacker, null);
        assertThat(before.getStatusCode().value()).isEqualTo(200);
        assertProblem(putWithoutBody(updatePath, attacker), 404, "NOT_FOUND");
        assertThat(get(readPath, attacker, null).getBody()).isEqualTo(before.getBody());
    }

    private long tableCount(String table) throws Exception {
        if (!List.of("place_photos", "item_photos", "place_visit_photos", "recipe_photos", "film_photos",
                "why_fun_venue_photos", "why_fun_visit_photos", "special_date_occurrence_photos").contains(table)) {
            throw new IllegalArgumentException("Unexpected photo table");
        }
        try (Connection connection = adminConnection()) {
            return scalarLong(connection, "select count(*) from " + table);
        }
    }

    private void assertCoupleScopedDetail(String pathPrefix, Long idA, Long idB, String markerA) {
        ResponseEntity<String> ownA = get(pathPrefix + idA, USER_A1_AUTH_ID, null);
        ResponseEntity<String> hiddenFromA = get(pathPrefix + idB, USER_A1_AUTH_ID, null);
        ResponseEntity<String> ownB = get(pathPrefix + idB, USER_B_AUTH_ID, null);
        ResponseEntity<String> hiddenFromB = get(pathPrefix + idA, USER_B_AUTH_ID, null);
        assertThat(ownA.getStatusCode().value()).as(pathPrefix + idA).isEqualTo(200);
        assertThat(ownA.getBody()).as(pathPrefix + idA).contains(markerA);
        assertThat(hiddenFromA.getStatusCode().value()).as(pathPrefix + idB + " for A").isEqualTo(404);
        assertThat(hiddenFromB.getStatusCode().value()).as(pathPrefix + idA + " for B").isEqualTo(404);
        assertThat(ownB.getStatusCode().value()).as(pathPrefix + idB + " for B").isEqualTo(200);
    }

    private void assertCoupleScopedOccurrence(Fixture fixture) {
        String prefix = "/api/when-dates/special-dates/";
        for (UUID member : List.of(USER_A1_AUTH_ID, USER_B_AUTH_ID)) {
            Long ownDate = member.equals(USER_A1_AUTH_ID) ? fixture.specialDateA() : fixture.specialDateB();
            Long otherDate = member.equals(USER_A1_AUTH_ID) ? fixture.specialDateB() : fixture.specialDateA();
            assertThat(get(prefix + ownDate + "/occurrences/" + fixture.occurrenceDate(), member, null)
                    .getStatusCode().value()).as("member %s reads own occurrence", member).isEqualTo(200);
            assertThat(get(prefix + otherDate + "/occurrences/" + fixture.occurrenceDate(), member, null)
                    .getStatusCode().value()).as("member %s cannot read other occurrence", member).isEqualTo(404);
        }
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

    private ResponseEntity<String> postPhoto(String path, UUID subject) {
        HttpHeaders headers = authHeaders(subject);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(new byte[] {1, 2, 3}) {
            @Override
            public String getFilename() {
                return "cross-couple.webp";
            }
        });
        return http.postForEntity(url(path), new HttpEntity<>(parts, headers), String.class);
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
                 "acceptsReservations":false,"categoryId":%d,"tagIds":[],"zoneId":1}
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

    private ResponseEntity<byte[]> getPrivatePhoto(String path, UUID subject) {
        return http.exchange(url(path), HttpMethod.GET,
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

    private InvitationRaceFixture createAcceptedPair(String usernamePrefix) {
        User owner = createTestUser(usernamePrefix + "-owner");
        User partner = createTestUser(usernamePrefix + "-partner");
        CoupleContext.clear();
        try {
            CoupleService.CoupleSnapshot created = coupleService.create(owner);
            CoupleService.InvitationSnapshot invitation = coupleService.createInvitation(owner);
            coupleService.accept(invitation.token(), partner);
            return new InvitationRaceFixture(created.id(), owner, invitation.id());
        } finally {
            CoupleContext.clear();
        }
    }

    private PendingInvitationFixture createPendingInvitation(String usernamePrefix) {
        User owner = createTestUser(usernamePrefix + "-owner");
        User invitee = createTestUser(usernamePrefix + "-invitee");
        CoupleContext.clear();
        try {
            CoupleService.CoupleSnapshot created = coupleService.create(owner);
            CoupleService.InvitationSnapshot invitation = coupleService.createInvitation(owner);
            return new PendingInvitationFixture(created.id(), invitation.id(), invitation.token(), invitee);
        } finally {
            CoupleContext.clear();
        }
    }

    private User createTestUser(String username) {
        User user = new User();
        user.username = username;
        user.authUserId = UUID.randomUUID();
        user.role = Role.USER;
        return users.saveAndFlush(user);
    }

    private static void lockCouple(Connection connection, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from couples where id = ? for update")) {
            statement.setObject(1, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private static void awaitCoupleLockWaiter(Connection admin) throws Exception {
        awaitDatabaseLockWaiter(admin);
    }

    private static void markMemberLeft(Connection admin, UUID coupleId, Long userId) throws Exception {
        try (PreparedStatement statement = admin.prepareStatement("""
                update couple_members
                set status = 'LEFT', left_at = now(), version = version + 1
                where couple_id = ? and user_id = ? and status = 'ACTIVE'
                """)) {
            statement.setObject(1, coupleId);
            statement.setLong(2, userId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static void revokeInvitation(Connection admin, long invitationId) throws Exception {
        try (PreparedStatement statement = admin.prepareStatement("""
                update couple_invitations
                set status = 'REVOKED', revoked_at = now()
                where id = ? and status = 'PENDING'
                """)) {
            statement.setLong(1, invitationId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static int activeMembershipCount(Long userId) throws Exception {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "select count(*) from couple_members where user_id = ? and status = 'ACTIVE'")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int activeMemberCount(UUID coupleId) throws Exception {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "select count(*) from couple_members where couple_id = ? and status = 'ACTIVE'")) {
            statement.setObject(1, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int countPendingInvitations(Connection admin, UUID coupleId) throws Exception {
        try (PreparedStatement statement = admin.prepareStatement(
                "select count(*) from couple_invitations where couple_id = ? and status = 'PENDING'")) {
            statement.setObject(1, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static String invitationStatus(long invitationId) throws Exception {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "select status from couple_invitations where id = ?")) {
            statement.setLong(1, invitationId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private void assertAdminMemberCanReadOwnCityCatalogs(Fixture fixture) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement role = connection.prepareStatement(
                "update users set role = 'ADMIN' where auth_user_id = ?")) {
            role.setObject(1, USER_A1_AUTH_ID);
            role.executeUpdate();
        }
        try {
            ResponseEntity<String> context = get("/api/location-context", USER_A1_AUTH_ID, null);
            assertThat(context.getStatusCode().value()).isEqualTo(200);
            JsonNode location = objectMapper.readTree(context.getBody());
            assertThat(location.path("coupleId").asText()).isEqualTo(COUPLE_A_ID.toString());
            assertThat(location.path("originCityId").asLong()).isEqualTo(1);
            assertThat(context.getBody()).contains("Rosario");

            for (var catalog : java.util.Map.of(
                    "/api/places", "Private place A",
                    "/api/films", "Private film A",
                    "/api/how-cook/recipes", "Torta pareja A",
                    "/api/why-fun/activities", "Museo pareja A",
                    "/api/when-dates", "Private anniversary A").entrySet()) {
                for (String filter : List.of("", "?cityId=1", "?zoneId=1")) {
                    ResponseEntity<String> result = get(catalog.getKey() + filter, USER_A1_AUTH_ID, null);
                    assertThat(result.getStatusCode().value()).as(catalog.getKey() + filter).isEqualTo(200);
                    assertThat(result.getBody()).contains(catalog.getValue())
                            .doesNotContain("Private place B", "Private film B", "Torta pareja B",
                                    "Museo pareja B", "Private anniversary B");
                }
                ResponseEntity<String> otherCity = get(catalog.getKey() + "?cityId=2", USER_A1_AUTH_ID, null);
                assertThat(otherCity.getStatusCode().value()).isEqualTo(200);
                assertThat(otherCity.getBody()).doesNotContain(catalog.getValue());
            }
            assertThat(get("/api/places/" + fixture.placeB(), USER_A1_AUTH_ID, null)
                    .getStatusCode().value()).isEqualTo(404);
            for (String photoPath : fixture.privatePhotoPaths()) {
                assertThat(getPrivatePhoto(photoPath, USER_A1_AUTH_ID).getStatusCode().value())
                        .as(photoPath).isEqualTo(404);
            }
            assertThat(get("/api/location-context", ADMIN_AUTH_ID, null).getStatusCode().value()).isEqualTo(403);
            assertThat(get("/api/categories/all", ADMIN_AUTH_ID, null).getStatusCode().value()).isEqualTo(200);
        } finally {
            try (Connection connection = adminConnection(); PreparedStatement role = connection.prepareStatement(
                    "update users set role = 'USER' where auth_user_id = ?")) {
                role.setObject(1, USER_A1_AUTH_ID);
                role.executeUpdate();
            }
        }
    }

    private Fixture seedFixture() throws Exception {
        try (Connection connection = adminConnection()) {
            List<String> privatePhotoPaths = new java.util.ArrayList<>();
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
            long placeVisitA = insertSpecialDateAndVisit(connection, placeA, userA1, COUPLE_A_ID, "Private anniversary A", today.minusYears(1), "ANNUAL", today);
            long placeVisitB = insertSpecialDateAndVisit(connection, placeB, userB, COUPLE_B_ID, "Private anniversary B", today.minusMonths(1), "MONTHLY", today);
            long archivedPlaceB = insertPlace(connection, "Archived private place B", categoryId, userB, COUPLE_B_ID);
            try (PreparedStatement archive = connection.prepareStatement(
                    "update places set deactivated_at = now() where id = ?")) {
                archive.setLong(1, archivedPlaceB);
                archive.executeUpdate();
            }
            insertPhoto(connection, placeB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/places/" + placeB + "/photo");
            long itemB = insertItem(connection, placeVisitB, userB, COUPLE_B_ID);
            insertSinglePhoto(connection, "item_photos", "item_id", itemB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/items/" + itemB + "/photo");
            long visitPhotoB = insertGalleryPhoto(connection, "place_visit_photos", "visit_id", placeVisitB, userB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/place-visit-photos/" + visitPhotoB);
            insertReview(connection, placeA, userA1, COUPLE_A_ID, "Review from member one");
            long recipeA = insertRecipe(connection, "Torta pareja A", userA1, COUPLE_A_ID);
            long recipeB = insertRecipe(connection, "Torta pareja B", userB, COUPLE_B_ID);
            insertSinglePhoto(connection, "recipe_photos", "recipe_id", recipeB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/how-cook/recipes/" + recipeB + "/photo");
            long cookingA = insertCooking(connection, recipeA, userA1, COUPLE_A_ID, "TOMAS");
            long cookingB = insertCooking(connection, recipeB, userB, COUPLE_B_ID, "AVRIL");
            insertCookingReview(connection, cookingA, userA1, COUPLE_A_ID, 5);
            insertCookingReview(connection, cookingB, userB, COUPLE_B_ID, 1);
            long filmA = insertFilm(connection, "Private film A", userA1, COUPLE_A_ID);
            long filmB = insertFilm(connection, "Private film B", userB, COUPLE_B_ID);
            insertSinglePhoto(connection, "film_photos", "film_id", filmB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/films/" + filmB + "/photo");
            long dramaGenre = scalarLong(connection, "select id from film_genre_options where lower(name) = 'drama' limit 1");
            insertFilmGenre(connection, filmA, dramaGenre, COUPLE_A_ID);
            insertFilmGenre(connection, filmB, dramaGenre, COUPLE_B_ID);
            long activityCategoryId = insertActivityCategory(connection, "HTTP Activity Test", "http-activity-test", null);
            long activitySubcategoryId = insertActivityCategory(connection, "HTTP Activity Subtest", "http-activity-subtest", activityCategoryId);
            long activityA = insertActivity(connection, "Museo pareja A", activityCategoryId, activitySubcategoryId, userA1, COUPLE_A_ID);
            long activityB = insertActivity(connection, "Museo pareja B", activityCategoryId, activitySubcategoryId, userB, COUPLE_B_ID);
            long activityVenuePhotoB = insertSinglePhoto(connection, "why_fun_venue_photos", "venue_id", activityB, COUPLE_B_ID);
            try (PreparedStatement cover = connection.prepareStatement(
                    "update why_fun_venues set cover_photo_id = ? where id = ?")) {
                cover.setLong(1, activityVenuePhotoB);
                cover.setLong(2, activityB);
                cover.executeUpdate();
            }
            privatePhotoPaths.add("/api/why-fun/activities/" + activityB + "/photo");
            long activityVisitA = insertActivityVisit(connection, activityA, userA1, COUPLE_A_ID);
            long activityVisitB = insertActivityVisit(connection, activityB, userB, COUPLE_B_ID);
            long activityVisitPhotoB = insertGalleryPhoto(connection, "why_fun_visit_photos", "visit_id", activityVisitB, userB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/why-fun/activity-visit-photos/" + activityVisitPhotoB);
            privatePhotoPaths.add("/api/why-fun/photos/" + activityVenuePhotoB);
            long specialDateB = scalarLong(connection, "select id from special_dates where label = 'Private anniversary B'");
            long specialDateA = scalarLong(connection, "select id from special_dates where label = 'Private anniversary A'");
            long occurrenceA = insertOccurrence(connection, specialDateA, today, userA1, COUPLE_A_ID);
            long occurrenceB = insertOccurrence(connection, specialDateB, today, userB, COUPLE_B_ID);
            long occurrencePhotoB = insertGalleryPhoto(connection, "special_date_occurrence_photos", "occurrence_id", occurrenceB, userB, COUPLE_B_ID);
            privatePhotoPaths.add("/api/when-dates/photos/" + occurrencePhotoB);
            insertActivityReview(connection, activityVisitA, userA1, COUPLE_A_ID, 5);
            insertActivityReview(connection, activityVisitB, userB, COUPLE_B_ID, 1);
            return new Fixture(placeA, placeB, categoryId, activityCategoryId, activitySubcategoryId,
                    placeVisitA, placeVisitB, itemB, recipeA, recipeB, cookingA, cookingB, filmA, filmB,
                    activityA, activityB, activityVisitA, activityVisitB, specialDateA, specialDateB,
                    visitPhotoB, activityVenuePhotoB, activityVisitPhotoB, occurrenceA, occurrencePhotoB, today,
                    List.copyOf(privatePhotoPaths));
        }
    }

    private void benchmarkCalendarSummaryQuery(Fixture fixture) throws Exception {
        int syntheticVisitCount = 5_000;
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        try (Connection connection = adminConnection()) {
            long authorId = scalarLong(connection, "select id from users where username = 'http-user-a1'");
            try (PreparedStatement specialDates = connection.prepareStatement("""
                    insert into special_dates(special_date, label, recurrence, couple_id)
                    select ?::date - event.day_offset, 'Synthetic calendar event ' || event.day_offset,
                           'ONCE', ?::uuid
                    from generate_series(1, ?) as event(day_offset)
                    """)) {
                specialDates.setObject(1, today);
                specialDates.setObject(2, COUPLE_A_ID);
                specialDates.setInt(3, syntheticVisitCount);
                specialDates.executeUpdate();
            }
            try (PreparedStatement visits = connection.prepareStatement("""
                    insert into place_visits(place_id, visited_on, created_by, updated_by, couple_id)
                    select ?, ?::date - event.day_offset, ?, ?, ?::uuid
                    from generate_series(1, ?) as event(day_offset)
                    """)) {
                visits.setLong(1, fixture.placeA());
                visits.setObject(2, today);
                visits.setLong(3, authorId);
                visits.setLong(4, authorId);
                visits.setObject(5, COUPLE_A_ID);
                visits.setInt(6, syntheticVisitCount);
                visits.executeUpdate();
            }
            try (java.sql.Statement analyze = connection.createStatement()) {
                analyze.execute("ANALYZE place_visits");
                analyze.execute("ANALYZE special_dates");
            }

            Query repositoryQuery = Repositories.SpecialDates.class
                    .getMethod("findSummaryPageByCoupleId", UUID.class, Long.class, Long.class, LocalDate.class, int.class, long.class)
                    .getAnnotation(Query.class);
            String sql = repositoryQuery.value()
                    .replace(":coupleId", "'" + COUPLE_A_ID + "'::uuid")
                    .replace(":specialDateId", "NULL")
                    .replace(":zoneId", "NULL")
                    .replace(":today", "'" + today + "'::date")
                    .replace(":limit", "31")
                    .replace(":offset", "0");
            List<String> plan = new java.util.ArrayList<>();
            try (java.sql.Statement explain = connection.createStatement();
                    ResultSet result = explain.executeQuery("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql)) {
                while (result.next()) plan.add(result.getString(1));
            }
            String planText = String.join("\n", plan);
            assertThat(planText).contains("Execution Time:");
            Matcher removedRows = Pattern.compile("Rows Removed by Join Filter: (\\d+)").matcher(planText);
            long maximumJoinFilterRejections = 0;
            while (removedRows.find()) {
                maximumJoinFilterRejections = Math.max(maximumJoinFilterRejections, Long.parseLong(removedRows.group(1)));
            }
            assertThat(maximumJoinFilterRejections).isLessThan(syntheticVisitCount * 20L);
            System.out.println("C21_CALENDAR_EXPLAIN syntheticCoupleAVisits=" + syntheticVisitCount + "\n" + planText);
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

    private static long insertSpecialDateAndVisit(Connection connection, long placeId, long userId, UUID coupleId,
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
                values (?, ?, ?, ?, ?) returning id
                """)) {
            visit.setLong(1, placeId); visit.setObject(2, visitedOn); visit.setLong(3, userId); visit.setLong(4, userId);
            visit.setObject(5, coupleId);
            try (ResultSet result = visit.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static long insertOccurrence(Connection connection, long specialDateId, LocalDate occurredOn,
            long authorId, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into special_date_occurrences(special_date_id, occurred_on, created_by, updated_by, couple_id)
                values (?, ?, ?, ?, ?) returning id
                """)) {
            statement.setLong(1, specialDateId);
            statement.setObject(2, occurredOn);
            statement.setLong(3, authorId);
            statement.setLong(4, authorId);
            statement.setObject(5, coupleId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static long insertSinglePhoto(Connection connection, String table, String parentColumn, long parentId,
            UUID coupleId) throws Exception {
        String sql = "insert into " + table + "(" + parentColumn
                + ", image_base64, thumbnail_base64, width, height, couple_id) values (?, ?, ?, 1, 1, ?) returning id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parentId);
            statement.setString(2, encodedPhoto(table + "-full"));
            statement.setString(3, encodedPhoto(table + "-thumbnail"));
            statement.setObject(4, coupleId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static long insertGalleryPhoto(Connection connection, String table, String parentColumn, long parentId,
            long authorId, UUID coupleId) throws Exception {
        return insertGalleryPhoto(connection, table, parentColumn, parentId, authorId, coupleId, 0);
    }

    private static long insertGalleryPhoto(Connection connection, String table, String parentColumn, long parentId,
            long authorId, UUID coupleId, int position) throws Exception {
        String sql = "insert into " + table + "(" + parentColumn
                + ", image_base64, thumbnail_base64, width, height, position, created_by, couple_id)"
                + " values (?, ?, ?, 1, 1, ?, ?, ?) returning id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parentId);
            statement.setString(2, encodedPhoto(table + "-full"));
            statement.setString(3, encodedPhoto(table + "-thumbnail"));
            statement.setInt(4, position);
            statement.setLong(5, authorId);
            statement.setObject(6, coupleId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static String encodedPhoto(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static long insertPlaceVisitForHistoryTest(long placeId, LocalDate visitedOn, String username,
            UUID coupleId) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "insert into place_visits(place_id, visited_on, created_by, updated_by, couple_id) "
                        + "select ?, ?, u.id, u.id, ? from users u where u.username = ? returning id")) {
            statement.setLong(1, placeId);
            statement.setDate(2, java.sql.Date.valueOf(visitedOn));
            statement.setObject(3, coupleId);
            statement.setString(4, username);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                long id = result.getLong(1);
                assertThat(result.next()).isFalse();
                return id;
            }
        }
    }

    private static long insertItem(Connection connection, long visitId, long authorId, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into items(visit_id, created_by, name, couple_id)
                values (?, ?, 'Private photo item B', ?) returning id
                """)) {
            statement.setLong(1, visitId);
            statement.setLong(2, authorId);
            statement.setObject(3, coupleId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); }
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
        assertThat(place.getBody()).contains("Review from member one", "Updated review from member two")
                .doesNotContain("Review from member two\"", "Cross-couple review");
        try (Connection connection = adminConnection(); PreparedStatement statement = connection.prepareStatement(
                "select count(*), min(comment), max(comment) from place_reviews where place_id = ?")) {
            statement.setLong(1, fixture.placeA());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
                assertThat(result.getString(2)).isEqualTo("Review from member one");
                assertThat(result.getString(3)).isEqualTo("Updated review from member two");
            }
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void assertRuntimeDatabaseRoleIsRestricted() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "whatplan_runtime",
                "test-only-runtime-password-0123456789");
                PreparedStatement statement = connection.prepareStatement("""
                        select current_user, r.rolsuper, r.rolbypassrls,
                               row_security_active('public.places'::regclass)
                        from pg_roles r where r.rolname = current_user
                        """);
                ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("whatplan_runtime");
            assertThat(result.getBoolean(2)).isFalse();
            assertThat(result.getBoolean(3)).isFalse();
            assertThat(result.getBoolean(4)).isTrue();
        }
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
            Long activitySubcategoryId, Long placeVisitA, Long placeVisitB, Long itemB,
            Long recipeA, Long recipeB, Long cookingA, Long cookingB, Long filmA, Long filmB,
            Long activityA, Long activityB, Long activityVisitA, Long activityVisitB,
            Long specialDateA, Long specialDateB, Long placeVisitPhotoB, Long activityVenuePhotoB,
            Long activityVisitPhotoB,
            Long occurrenceA, Long occurrencePhotoB, LocalDate occurrenceDate,
            List<String> privatePhotoPaths) {}
    private record UploadAttempt(String path, String table) {}
    private record InvitationRaceFixture(UUID coupleId, User owner, long invitationId) {}
    private record PendingInvitationFixture(UUID coupleId, long invitationId, String token, User invitee) {}
    @FunctionalInterface
    private interface GalleryUpload {
        void upload(MockMultipartFile file) throws Exception;
    }
}
