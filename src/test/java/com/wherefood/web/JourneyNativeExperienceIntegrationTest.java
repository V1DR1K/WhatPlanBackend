package com.wherefood.web;

import static com.wherefood.journey.JourneyDtos.*;

import static org.assertj.core.api.Assertions.*;

import com.wherefood.config.*;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.User;
import com.wherefood.journey.*;
import com.wherefood.journey.JourneyPersistenceIntegrationTest.TestDatabase;
import com.wherefood.repo.Repositories.Users;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;

import java.time.LocalDate;
import java.util.*;

@DataJpaTest(
        showSql = false,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JourneyService.class,
    LocationService.class,
    JourneySourceRepository.class,
    TenantDataSourcePostProcessor.class,
    FilmViewService.class,
    CoupleAuthorizationService.class
})
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class JourneyNativeExperienceIntegrationTest {
    @Container
    static final TestDatabase DATABASE =
            new TestDatabase()
                    .withUsername("journey_test")
                    .withPassword("test-only-journey-admin-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("DATABASE_URL", DATABASE::getJdbcUrl);
        p.add("DATABASE_USER", () -> "journey_runtime");
        p.add("DATABASE_PASSWORD", () -> "test-only-journey-runtime-password");
        p.add("spring.flyway.url", DATABASE::getJdbcUrl);
        p.add("spring.flyway.user", DATABASE::getUsername);
        p.add("spring.flyway.password", DATABASE::getPassword);
    }

    @Autowired JourneyService journey;
    @Autowired FilmViewService views;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired Users users;
    final LocalDate day = LocalDate.of(2026, 8, 10);

    @BeforeEach
    void context() {
        CoupleContext.set(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @AfterEach
    void clear() {
        CoupleContext.clear();
    }

    Long film() {
        return jdbc.queryForObject(
                "insert into films(title,created_by,updated_by,zone_id) values('Native"
                    + " experience',1,1,1) returning id",
                Map.of(),
                Long.class);
    }

    TripDto trip() {
        return journey.saveTrip(
                null,
                new TripRequest(
                        "Native linkage",
                        day,
                        day.plusDays(1),
                        List.of(new StageRequest(null, 2L, day, day.plusDays(1)))));
    }

    @Test
    void nativeExperienceCreationAndEditCompleteOnePointWithoutStaleJpaVersion() {
        TripDto trip = trip();
        UUID stage = trip.stages().getFirst().id();
        Long film = film();
        User actor = users.findById(1L).orElseThrow();
        journey.pending("FILM", film, stage);
        UUID point = journey.detail(trip.id()).points().getFirst().id();
        var view = views.create(film, new FilmViewRequest(day, 2L, stage, point), actor);
        assertThat(view.cityId).isEqualTo(2L);
        assertThat(view.stageId).isEqualTo(stage);
        views.update(film, view.id, new FilmViewRequest(day.plusDays(1), 2L, stage, null), actor);
        var linked = journey.detail(trip.id()).points();
        assertThat(linked).hasSize(1);
        assertThat(linked.getFirst().id()).isEqualTo(point);
        assertThat(linked.getFirst().scheduledOn()).isEqualTo(day.plusDays(1));
        assertThat(linked.getFirst().status()).isEqualTo("COMPLETED");
    }

    @Test
    void invalidPointRollsBackBothNativeExperienceAndDerivedSummary() {
        TripDto trip = trip();
        UUID stage = trip.stages().getFirst().id();
        Long film = film();
        Long otherFilm = film();
        User actor = users.findById(1L).orElseThrow();
        var point =
                journey.savePoint(
                        trip.id(),
                        null,
                        new PointRequest(
                                stage,
                                "Other film",
                                day,
                                null,
                                null,
                                null,
                                0,
                                "PENDING",
                                new SourceRef("FILM", otherFilm, null)));
        assertThatThrownBy(
                        () ->
                                views.create(
                                        film,
                                        new FilmViewRequest(day, 2L, stage, point.id()),
                                        actor))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from film_views where film_id=:id",
                                Map.of("id", film),
                                Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select watched_count from films where id=:id",
                                Map.of("id", film),
                                Integer.class))
                .isZero();
        assertThat(journey.detail(trip.id()).points().getFirst().source().experienceId()).isNull();
    }

    @Test
    void aDifferentCoupleCannotBindOrMutateExperienceOrReadTrip() {
        TripDto trip = trip();
        Long film = film();
        User actor = users.findById(1L).orElseThrow();
        var view = views.create(film, new FilmViewRequest(day), actor);
        CoupleContext.set(UUID.randomUUID());
        assertThatThrownBy(
                        () ->
                                journey.bind(
                                        "FILM",
                                        film,
                                        view.id,
                                        new BindingRequest(
                                                2L, trip.stages().getFirst().id(), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> journey.detail(trip.id()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                views.update(
                                        film, view.id, new FilmViewRequest(day.plusDays(1)), actor))
                .isInstanceOf(ResponseStatusException.class);
    }
}
