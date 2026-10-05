package com.wherefood.journey;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;

import java.sql.*;
import java.util.*;

@Testcontainers
class JourneyMigrationTest {
    @Container
    static final JourneyPersistenceIntegrationTest.TestDatabase DATABASE =
            new JourneyPersistenceIntegrationTest.TestDatabase()
                    .withUsername("journey_test")
                    .withPassword("test-only-journey-admin-password");

    @Test
    void migrationPreservesMediaAndReviewsAndBackfillsEveryExistingCityUnderRestrictedMigrator()
            throws Exception {
        var flyway =
                Flyway.configure()
                        .dataSource(
                                DATABASE.getJdbcUrl(),
                                DATABASE.getUsername(),
                                DATABASE.getPassword())
                        .target("63")
                        .load();
        flyway.migrate();
        try (Connection c =
                        DriverManager.getConnection(
                                DATABASE.getJdbcUrl(),
                                DATABASE.getUsername(),
                                DATABASE.getPassword());
                Statement s = c.createStatement()) {
            s.execute(
                    "insert into"
                        + " places(id,name,category_id,created_by,updated_by,couple_id,zone_id)"
                        + " values(8100,'Legacy"
                        + " restaurant',1,1,1,'00000000-0000-0000-0000-000000000001',2)");
            s.execute(
                    "insert into"
                        + " place_visits(id,place_id,visited_on,created_by,updated_by,couple_id)"
                        + " values(8101,8100,'2026-07-01',1,1,'00000000-0000-0000-0000-000000000001')");
            s.execute(
                    "insert into"
                        + " place_photos(place_id,image_base64,thumbnail_base64,width,height,couple_id)"
                        + " values(8100,'original-photo','original-thumbnail',10,20,'00000000-0000-0000-0000-000000000001')");
            s.execute(
                    "insert into place_reviews(place_id,author_id,comment,couple_id)"
                        + " values(8100,1,'Reseña"
                        + " conservada','00000000-0000-0000-0000-000000000001')");
            s.execute(
                    "insert into special_dates(special_date,label,recurrence,couple_id)"
                        + " values(date '2026-01-07','Legacy important range','ONCE',"
                        + "'00000000-0000-0000-0000-000000000001')");
            s.execute(
                    "insert into special_date_occurrences(special_date_id,occurred_on,created_by,updated_by,couple_id)"
                        + " select id,date '2026-01-08',1,1,couple_id from special_dates"
                        + " where label='Legacy important range'");
            String role = "migrator_" + UUID.randomUUID().toString().replace("-", "");
            s.execute(
                    "create role "
                            + role
                            + " login password 'test-only-migrator-password' nosuperuser"
                            + " nobypassrls");
            s.execute("grant usage,create on schema public to " + role);
            s.execute("grant " + role + " to journey_test");
            // Match production: the migration login owns tables, without SUPERUSER/BYPASSRLS.
            try (ResultSet tables =
                    s.executeQuery("select tablename from pg_tables where schemaname='public'")) {
                List<String> names = new ArrayList<>();
                while (tables.next()) names.add(tables.getString(1));
                for (String table : names)
                    try (Statement alter = c.createStatement()) {
                        alter.execute("alter table " + table + " owner to " + role);
                    }
            }
            Flyway complete =
                    Flyway.configure()
                            .dataSource(DATABASE.getJdbcUrl(), role, "test-only-migrator-password")
                            .load();
            complete.migrate();
            complete.validate();
            try (ResultSet r =
                    s.executeQuery(
                            "select"
                                + " p.zone_id,v.city_id,v.stage_id,ph.image_base64,ph.thumbnail_base64,rv.comment"
                                + " from places p join place_visits v on v.place_id=p.id join"
                                + " place_photos ph on ph.place_id=p.id join place_reviews rv on"
                                + " rv.place_id=p.id where p.id=8100")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getLong(1)).isEqualTo(1L);
                assertThat(r.getLong(2)).isEqualTo(1L);
                assertThat(r.getObject(3)).isNull();
                assertThat(r.getString(4)).isEqualTo("original-photo");
                assertThat(r.getString(5)).isEqualTo("original-thumbnail");
                assertThat(r.getString(6)).isEqualTo("Reseña conservada");
            }
            try (ResultSet r = s.executeQuery("select count(*) from journeys")) {
                r.next();
                assertThat(r.getInt(1)).isZero();
            }
            try (ResultSet r = s.executeQuery(
                    "select special_date,ends_on from special_dates where label='Legacy important range'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getDate(2)).isEqualTo(r.getDate(1));
            }
            try (ResultSet r = s.executeQuery(
                    "select occurred_on,ends_on from special_date_occurrences"
                        + " where special_date_id=(select id from special_dates where label='Legacy important range')")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getDate(2)).isEqualTo(r.getDate(1));
            }
            try (ResultSet r =
                    s.executeQuery(
                            "select count(*) from pg_class where relname in"
                                + " ('places','films','recipes','why_fun_venues','journeys','journey_files',"
                                + "'special_dates','special_date_occurrences')"
                                + " and relrowsecurity and relforcerowsecurity")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(8);
            }
        }
    }
}
