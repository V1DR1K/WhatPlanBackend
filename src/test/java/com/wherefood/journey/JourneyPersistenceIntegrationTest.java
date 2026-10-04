package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import static org.assertj.core.api.Assertions.*;

import com.wherefood.config.*;
import com.wherefood.domain.*;
import com.wherefood.web.PhotoStorage;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Real PostgreSQL persistence, migrations, scoped repositories and atomic source linkage. */
@DataJpaTest(
        showSql = false,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JourneyService.class,
    JourneyPointTypeService.class,
    LocationService.class,
    JourneySourceRepository.class,
    TenantDataSourcePostProcessor.class
})
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
@Testcontainers
public class JourneyPersistenceIntegrationTest {
    static final UUID COUPLE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final LocalDate DAY = LocalDate.of(2026, 8, 10);

    @Container
    static final TestDatabase DATABASE =
            new TestDatabase()
                    .withUsername("journey_test")
                    .withPassword("test-only-journey-admin-password");

    public static class TestDatabase extends PostgreSQLContainer<TestDatabase> {
        public TestDatabase() {
            super("postgres:16-alpine");
        }

        String externalUrl;

        @Override
        public void start() {
            String external = System.getProperty("journey.test.jdbc-url");
            if (external == null) super.start();
            else {
                String database = "journey_" + UUID.randomUUID().toString().replace("-", "");
                try (Connection c =
                                DriverManager.getConnection(
                                        external, getUsername(), getPassword());
                        Statement s = c.createStatement()) {
                    s.execute("create database " + database);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
                externalUrl = external.substring(0, external.lastIndexOf('/') + 1) + database;
            }
            try (Connection c =
                            DriverManager.getConnection(
                                    getJdbcUrl(), getUsername(), getPassword());
                    Statement s = c.createStatement()) {
                s.execute(
                        "DO $$ BEGIN IF NOT EXISTS(select 1 from pg_roles where"
                            + " rolname='journey_runtime') THEN CREATE ROLE journey_runtime LOGIN"
                            + " PASSWORD 'test-only-journey-runtime-password' NOSUPERUSER"
                            + " NOBYPASSRLS; END IF; END $$");
                s.execute("grant usage on schema public to journey_runtime");
                s.execute(
                        "alter default privileges in schema public grant"
                                + " select,insert,update,delete on tables to journey_runtime");
                s.execute(
                        "alter default privileges in schema public grant usage,select on sequences"
                                + " to journey_runtime");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String getJdbcUrl() {
            return externalUrl == null ? super.getJdbcUrl() : externalUrl;
        }

        @Override
        public void stop() {
            if (externalUrl == null) super.stop();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("DATABASE_URL", DATABASE::getJdbcUrl);
        p.add("DATABASE_USER", () -> "journey_runtime");
        p.add("DATABASE_PASSWORD", () -> "test-only-journey-runtime-password");
        p.add("spring.flyway.url", DATABASE::getJdbcUrl);
        p.add("spring.flyway.user", DATABASE::getUsername);
        p.add("spring.flyway.password", DATABASE::getPassword);
    }

    @Autowired JourneyService service;
    @Autowired JourneyPointTypeService pointTypes;
    @Autowired LocationService locations;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @MockitoBean PhotoStorage photoStorage;

    @BeforeEach
    void context() {
        CoupleContext.set(COUPLE);
    }

    @AfterEach
    void clear() {
        CoupleContext.clear();
    }

    TripDto trip() {
        return service.saveTrip(
                null,
                new TripRequest(
                        "Buenos Aires",
                        DAY,
                        DAY.plusDays(2),
                        List.of(new StageRequest(null, 2L, DAY, DAY.plusDays(2)))));
    }

    PointRequest point(UUID stage, String status, SourceRef ref) {
        return new PointRequest(stage, "Paseo", DAY, null, null, null, 0, status, ref);
    }

    @Test
    void repeatCityUsesOneCatalogueAndDistinctTripContent() {
        TripDto a = trip(), b = trip();
        service.savePoint(a.id(), null, point(a.stages().getFirst().id(), "PENDING", null));
        assertThat(service.detail(a.id()).points()).hasSize(1);
        assertThat(service.detail(b.id()).points()).isEmpty();
        var options =
                locations.context().options().stream().filter(o -> o.cityId().equals(2L)).toList();
        assertThat(options).hasSize(2);
        assertThat(options.get(0).key()).isNotEqualTo(options.get(1).key());
    }

    @Test
    void agendaTypesAndCustomActionsPersistIndependentOfLinkedSource() {
        TripDto t = trip();
        UUID stage = t.stages().getFirst().id();
        PointDto saved = service.savePoint(t.id(), null, new PointRequest(
                stage, "Subte a Retiro", DAY, null, null, null, 0, "PENDING", null,
                "TRANSFER", List.of(new PointActionRequest("Horarios", "WEB", "https://example.com/horarios"))));

        assertThat(saved.category()).isEqualTo("TRANSFER");
        assertThat(saved.extraActions()).containsExactly(new PointActionDto("Horarios", "WEB", "https://example.com/horarios"));
        assertThat(service.detail(t.id()).points().getFirst().category()).isEqualTo("TRANSFER");

        Long film = film();
        PointDto linked =
                service.savePoint(
                        t.id(),
                        null,
                        new PointRequest(
                                stage,
                                "Peli",
                                DAY,
                                null,
                                null,
                                null,
                                1,
                                "PENDING",
                                new SourceRef("FILM", film, null),
                                "TRANSFER"));
        assertThat(linked.category()).isEqualTo("TRANSFER");
    }

    @Test
    void customPointTypeIsIsolatedToTheActiveCoupleAndCanBeAssigned() {
        TripDto t = trip();
        PointTypeDto custom = pointTypes.create(new PointTypeRequest("Compras", "SHOP", "#2375A8"));
        UUID stage = t.stages().getFirst().id();

        PointDto point = service.savePoint(t.id(), null,
                new PointRequest(stage, "Feria", DAY, null, null, null, 0, "PENDING", null, custom.code()));

        assertThat(point.category()).isEqualTo(custom.code());
        assertThat(pointTypes.list()).extracting(PointTypeDto::code).contains(custom.code());
    }

    @Test
    void originChangeIsSharedAndNeverReassignsHistory() {
        Long film = film();
        Long exp = experience(film);
        locations.saveOrigin(2L);
        assertThat(locations.origin()).isEqualTo(2L);
        assertThat(service.experienceLocation("FILM", film, exp).cityId()).isEqualTo(1L);
    }

    Long film() {
        return jdbc.queryForObject(
                "insert into films(title,created_by,updated_by,zone_id) values('Reusable"
                        + " film',1,1,1) returning id",
                Map.of(),
                Long.class);
    }

    Long experience(Long film) {
        return jdbc.queryForObject(
                "insert into film_views(film_id,watched_on,created_by,updated_by)"
                        + " values(:film,:date,1,1) returning id",
                Map.of("film", film, "date", DAY),
                Long.class);
    }

    @Test
    void existingExperienceCompletesPendingPointWithoutDuplicatingAndPreservesLedgerOnEdit() {
        TripDto t = trip();
        UUID stage = t.stages().getFirst().id();
        Long film = film(), exp = experience(film);
        service.pending("FILM", film, stage);
        UUID point = service.detail(t.id()).points().getFirst().id();
        service.bind("FILM", film, exp, new BindingRequest(2L, stage, point));
        service.saveMovement(
                t.id(),
                null,
                new MovementRequest(
                        stage,
                        point,
                        null,
                        "EXPENSE",
                        "Entrada",
                        new BigDecimal("123.45"),
                        "ARS",
                        DAY));
        service.bind("FILM", film, exp, new BindingRequest(2L, stage, null));
        var detail = service.detail(t.id());
        assertThat(detail.points()).hasSize(1);
        assertThat(detail.points().getFirst().status()).isEqualTo("COMPLETED");
        assertThat(detail.movements().getFirst().pointId()).isEqualTo(point);
        assertThat(service.catalog("FILM", 1L, "")).hasSize(1);
        assertThat(service.catalog("FILM", 2L, "")).hasSize(1);
    }

    @Test
    void stageEditsRejectInvalidatingScheduledContent() {
        TripDto t = trip();
        UUID stage = t.stages().getFirst().id();
        service.savePoint(t.id(), null, point(stage, "PENDING", null));
        assertThatThrownBy(
                        () ->
                                service.saveTrip(
                                        t.id(),
                                        new TripRequest(
                                                t.name(),
                                                DAY,
                                                DAY.plusDays(2),
                                                List.of(
                                                        new StageRequest(
                                                                stage, 1L, DAY, DAY.plusDays(2))))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reubicá");
    }

    @Test
    void cancellationKeepsExpensesAndBalancesSeparateCurrencies() {
        TripDto t = trip();
        UUID stage = t.stages().getFirst().id();
        PointDto p = service.savePoint(t.id(), null, point(stage, "PENDING", null));
        for (var r :
                List.of(
                        new MovementRequest(
                                stage,
                                null,
                                null,
                                "FUNDS",
                                "Fondos",
                                new BigDecimal("1000"),
                                "ARS",
                                DAY),
                        new MovementRequest(
                                stage,
                                p.id(),
                                null,
                                "EXPENSE",
                                "Entrada",
                                new BigDecimal("100.10"),
                                "ARS",
                                DAY),
                        new MovementRequest(
                                stage,
                                p.id(),
                                null,
                                "REFUND",
                                "Devolución",
                                new BigDecimal("20.10"),
                                "ARS",
                                DAY),
                        new MovementRequest(
                                null,
                                null,
                                null,
                                "FUNDS",
                                "Dólares",
                                new BigDecimal("50"),
                                "USD",
                                DAY))) service.saveMovement(t.id(), null, r);
        service.savePoint(t.id(), p.id(), point(stage, "CANCELLED", null));
        var balances = service.detail(t.id()).balances();
        assertThat(balances).hasSize(2);
        assertThat(balances.getFirst().balance()).isEqualByComparingTo("920");
        assertThat(balances.getLast().balance()).isEqualByComparingTo("50");
    }

    @Test
    void hotelPriceDoesNotCountAsPaymentAndOriginalFilesAreAuthenticatedByCouple()
            throws Exception {
        TripDto t = trip();
        UUID stage = t.stages().getFirst().id();
        StayDto stay =
                service.saveStay(
                        t.id(),
                        null,
                        new StayRequest(
                                stage,
                                "Hotel",
                                DAY,
                                DAY.plusDays(1),
                                null,
                                new BigDecimal("200"),
                                "USD",
                                null,
                                null,
                                null));
        assertThat(service.detail(t.id()).balances()).isEmpty();
        byte[] pdf = "%PDF-1.7 original receipt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileDto f =
                service.upload(
                        t.id(),
                        stage,
                        null,
                        stay.id(),
                        null,
                        false,
                        new MockMultipartFile("file", "reserva.pdf", "application/pdf", pdf));
        assertThat(service.file(f.id()).content).isEqualTo(pdf);
        assertThat(service.detail(t.id()).files()).hasSize(1);
        CoupleContext.set(UUID.randomUUID());
        assertThatThrownBy(() -> service.file(f.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThat(service.list(0, 20)).isEmpty();
    }

    @Test
    void oneReusableFilmSupportsExperiencesInDifferentJourneys() {
        TripDto a = trip(), b = trip();
        Long f = film();
        Long first = experience(f);
        Long second =
                jdbc.queryForObject(
                        "insert into film_views(film_id,watched_on,created_by,updated_by)"
                                + " values(:film,:date,1,1) returning id",
                        Map.of("film", f, "date", DAY.plusDays(1)),
                        Long.class);
        service.bind("FILM", f, first, new BindingRequest(2L, a.stages().getFirst().id(), null));
        service.bind("FILM", f, second, new BindingRequest(2L, b.stages().getFirst().id(), null));
        assertThat(service.detail(a.id()).points().getFirst().source().experienceId())
                .isEqualTo(first);
        assertThat(service.detail(b.id()).points().getFirst().source().experienceId())
                .isEqualTo(second);
        assertThat(service.catalog("FILM", 2L, "")).hasSize(1);
    }

    @Autowired com.wherefood.repo.Repositories.SpecialDates dateTemplates;

    @Test
    void futureWhenDatesOccurrenceCanBelongDirectlyToJourneyWithoutExperiences() {
        LocalDate future = LocalDate.of(2027, 5, 15);
        TripDto t =
                service.saveTrip(
                        null,
                        new TripRequest(
                                "Aniversario",
                                future,
                                future.plusDays(1),
                                List.of(new StageRequest(null, 2L, future, future.plusDays(1)))));
        User actor = new User();
        actor.id = 1L;
        LinkedDateDto d =
                service.linkDate(
                        t.id(),
                        new DateLinkRequest(
                                t.stages().getFirst().id(), future, null, "Aniversario de viaje"),
                        actor);
        assertThat(service.detail(t.id()).dates()).contains(d);
        assertThat(
                        dateTemplates.findSummaryPageByCoupleId(
                                COUPLE, null, 2L, LocalDate.of(2026, 10, 3), 30, 0))
                .anyMatch(v -> v.getOccurredOn().equals(future) && v.getExperienceCount() == 0);
    }

    @Test
    void multipleCountriesAllowStagesSharingTravelDay() {
        CityDto city = locations.createCity(new CityRequest("Montevideo", "UY"));
        var t =
                service.saveTrip(
                        null,
                        new TripRequest(
                                "Río de la Plata",
                                DAY,
                                DAY.plusDays(2),
                                List.of(
                                        new StageRequest(null, 2L, DAY, DAY.plusDays(1)),
                                        new StageRequest(
                                                null,
                                                city.id(),
                                                DAY.plusDays(1),
                                                DAY.plusDays(2)))));
        assertThat(t.stages()).extracting(StageDto::countryCode).containsExactly("AR", "UY");
        assertThat(t.stages().getFirst().endsOn()).isEqualTo(t.stages().getLast().startsOn());
    }

    @Test
    void archiveRemovesFilterOptionsAndPreservesDetail() {
        TripDto t = trip();
        service.archive(t.id());
        assertThat(locations.context().options()).noneMatch(o -> t.id().equals(o.journeyId()));
        assertThat(service.detail(t.id()).trip().archived()).isTrue();
        assertThatThrownBy(
                        () ->
                                service.savePoint(
                                        t.id(),
                                        null,
                                        point(t.stages().getFirst().id(), "PENDING", null)))
                .hasMessageContaining("archivado");
    }

    @Test
    void bothPackingCreatesOneItemPerPartnerAndCheckedItemsMoveToTheEnd() {
        TripDto t = trip();
        List<PackingDto> created =
                service.savePackingForBoth(t.id(), new PackingBothRequest("Cargador", 2));

        assertThat(created).hasSize(2);
        assertThat(created).extracting(PackingDto::userId).doesNotHaveDuplicates();
        assertThat(created).extracting(PackingDto::position).containsOnly(0);

        Long firstMember = created.getFirst().userId();
        PackingDto second =
                service.savePacking(
                        t.id(), null, new PackingRequest(firstMember, "Pasaporte", 1, false));
        PackingDto packed =
                service.savePacking(
                        t.id(),
                        created.getFirst().id(),
                        new PackingRequest(firstMember, "Cargador", 2, true));

        assertThat(packed.position()).isGreaterThan(second.position());
        assertThat(service.detail(t.id()).packing())
                .filteredOn(item -> item.userId().equals(firstMember))
                .extracting(PackingDto::description)
                .containsExactly("Pasaporte", "Cargador");
    }

    @Test
    void packingCanBeReorderedOnlyWithinItsOwnersList() {
        TripDto t = trip();
        List<PackingDto> created =
                service.savePackingForBoth(t.id(), new PackingBothRequest("Llaves", 1));
        Long member = created.getFirst().userId();
        PackingDto second =
                service.savePacking(t.id(), null, new PackingRequest(member, "Documento", 1, false));

        service.reorderPacking(
                t.id(), new PackingOrderRequest(member, List.of(second.id(), created.getFirst().id())));

        assertThat(service.detail(t.id()).packing())
                .filteredOn(item -> item.userId().equals(member))
                .sorted(Comparator.comparingInt(PackingDto::position))
                .extracting(PackingDto::description)
                .containsExactly("Documento", "Llaves");
        assertThatThrownBy(
                        () -> service.reorderPacking(t.id(), new PackingOrderRequest(member, List.of(second.id()))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("todos los elementos");
    }

    @Test
    void databaseForeignKeysRejectReferencesAcrossCouplesEvenIfBothIdentifiersAreKnown() {
        TripDto trip = trip();
        UUID stage = trip.stages().getFirst().id();
        CoupleContext.set(UUID.randomUUID());
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into journey_points(id,journey_id,stage_id,title)"
                                                + " values(:id,:trip,:stage,'Forbidden')",
                                        Map.of(
                                                "id",
                                                UUID.randomUUID(),
                                                "trip",
                                                trip.id(),
                                                "stage",
                                                stage)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void experienceSelectorCanRecoverOlderEntriesBeyondItsFirstPage() {
        Long film = film();
        jdbc.update(
                "insert into film_views(film_id,watched_on,created_by,updated_by) select"
                    + " :film,cast(:date as date)-i,1,1 from generate_series(0,129) i",
                Map.of("film", film, "date", DAY));
        var first = service.experiences("FILM", film, DAY.minusDays(129), DAY, 0);
        var second = service.experiences("FILM", film, DAY.minusDays(129), DAY, 1);
        assertThat(first).hasSize(100);
        assertThat(second).hasSize(30);
        assertThat(second.getLast().date()).isEqualTo(DAY.minusDays(129));
    }
}
