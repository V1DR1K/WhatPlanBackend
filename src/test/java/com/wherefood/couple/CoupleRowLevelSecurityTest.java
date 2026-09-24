package com.wherefood.couple;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CoupleRowLevelSecurityTest {
    private static final String RUNTIME_USER = "whatplan_test_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-test-password";
    private static final UUID ORIGINAL_COUPLE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_COUPLE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<String> TENANT_TABLES = List.of(
            "places", "place_visits", "items", "item_photos", "item_reviews", "place_photos", "place_reviews",
            "place_visit_photos", "place_visit_reviews", "place_highlight_tags", "films", "film_photos",
            "film_reviews", "film_views", "film_genres", "recipes", "recipe_ingredients", "recipe_steps",
            "recipe_photos", "cookings", "cooking_reviews", "home_recipes", "home_recipe_ingredients",
            "home_recipe_steps", "home_recipe_photos", "home_recipe_reviews", "why_fun_venues",
            "why_fun_venue_schedules", "why_fun_venue_photos", "why_fun_venue_reviews", "why_fun_visits",
            "why_fun_visit_photos", "why_fun_visit_reviews", "special_dates", "special_date_occurrences",
            "special_date_occurrence_comments", "special_date_occurrence_photos");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateAndPrepareFixtures() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("44")
                .load()
                .migrate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into users(username, role) values ('tomas', 'USER'), ('avril', 'USER')");
            statement.executeUpdate("insert into places(name, category_id, created_by, updated_by) values ('Legacy place', 1, 1, 1)");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into couples(id, status, created_by) values ('" + OTHER_COUPLE + "', 'ACTIVE', 1)");
            statement.executeUpdate("insert into places(name, category_id, created_by, couple_id) values ('Other couple place', 1, 2, '" + OTHER_COUPLE + "')");
            statement.executeUpdate("insert into special_dates(id, special_date, label, couple_id) values (1001, date '2026-01-01', 'Legacy anniversary', '" + ORIGINAL_COUPLE + "'), (1002, date '2026-01-02', 'Private anniversary', '" + OTHER_COUPLE + "')");
            statement.executeUpdate("insert into special_date_occurrences(id, special_date_id, occurred_on, created_by, updated_by, couple_id) values (1101, 1001, date '2026-01-01', 1, 1, '" + ORIGINAL_COUPLE + "'), (1102, 1002, date '2026-01-02', 2, 2, '" + OTHER_COUPLE + "')");
            statement.executeUpdate("insert into special_date_occurrence_comments(id, occurrence_id, author_id, updated_by, comment, couple_id) values (1201, 1101, 1, 1, 'Shared memory', '" + ORIGINAL_COUPLE + "'), (1202, 1102, 2, 2, 'Private memory', '" + OTHER_COUPLE + "')");
            statement.executeUpdate("insert into special_date_occurrence_photos(id, occurrence_id, image_base64, thumbnail_base64, width, height, position, created_by, couple_id) values (1301, 1101, 'a', 'a', 1, 1, 0, 1, '" + ORIGINAL_COUPLE + "'), (1302, 1102, 'b', 'b', 1, 1, 0, 2, '" + OTHER_COUPLE + "')");
            statement.executeUpdate("create role " + RUNTIME_USER + " login password '" + RUNTIME_PASSWORD + "' nosuperuser nobypassrls");
            statement.executeUpdate("grant connect on database " + POSTGRES.getDatabaseName() + " to " + RUNTIME_USER);
            statement.executeUpdate("grant usage on schema public to " + RUNTIME_USER);
            statement.executeUpdate("grant select, insert, update, delete on all tables in schema public to " + RUNTIME_USER);
            statement.executeUpdate("grant usage, select on all sequences in schema public to " + RUNTIME_USER);
        }
    }

    @Test
    void everyPrivateTableHasForcedRowLevelSecurityAndCouplePolicy() throws Exception {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement("""
                select c.relrowsecurity, c.relforcerowsecurity,
                       exists (select 1 from pg_policies p
                               where p.schemaname = 'public' and p.tablename = c.relname
                                 and p.policyname = 'policy_' || c.relname || '_couple')
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relname = ?
                """)) {
            for (String table : TENANT_TABLES) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next(), "missing private table " + table);
                    assertTrue(result.getBoolean(1), "RLS disabled on " + table);
                    assertTrue(result.getBoolean(2), "FORCE RLS disabled on " + table);
                    assertTrue(result.getBoolean(3), "couple policy missing on " + table);
                }
            }
        }
    }

    @Test
    void runtimeRoleCannotReadOrChangeAnotherCouplesRowsAndUnsetContextSeesNothing() throws Exception {
        try (Connection runtime = runtimeConnection()) {
            assertEquals(0, countPlaces(runtime, null), "queries without tenant context must fail closed");
            assertEquals(1, countPlaces(runtime, ORIGINAL_COUPLE));
            assertEquals(1, countPlaces(runtime, OTHER_COUPLE));

            setCouple(runtime, ORIGINAL_COUPLE);
            assertEquals(1, countPlaces(runtime, null));
            assertEquals(0, updatePlace(runtime, "Other couple place"), "cross-couple update must affect no rows");
            assertEquals(0, deletePlace(runtime, "Other couple place"), "cross-couple delete must affect no rows");
            assertThrows(SQLException.class, () -> insertPlace(runtime, OTHER_COUPLE),
                    "RLS WITH CHECK must reject inserting a row into another couple");
            assertEquals(0, countByName(runtime, "Other couple place"));
            assertEquals(1, countByLabel(runtime, "Legacy anniversary"));
            assertEquals(0, countByLabel(runtime, "Private anniversary"));
            assertEquals(1, countById(runtime, "special_date_occurrences", 1101));
            assertEquals(0, countById(runtime, "special_date_occurrences", 1102));
            assertEquals(1, countById(runtime, "special_date_occurrence_comments", 1201));
            assertEquals(0, countById(runtime, "special_date_occurrence_comments", 1202));
            assertEquals(1, countById(runtime, "special_date_occurrence_photos", 1301));
            assertEquals(0, countById(runtime, "special_date_occurrence_photos", 1302));
            assertEquals(0, updateById(runtime, "special_date_occurrence_comments", 1202));
            assertEquals(0, deleteById(runtime, "special_date_occurrence_photos", 1302));

            setCouple(runtime, OTHER_COUPLE);
            assertEquals(1, countByName(runtime, "Other couple place"), "changing pooled-session context must scope the next query");
            assertEquals(0, countByName(runtime, "Legacy place"));
            assertEquals(1, countById(runtime, "special_date_occurrence_photos", 1302));
            assertEquals(0, countById(runtime, "special_date_occurrence_photos", 1301));
        }
    }

    @Test
    void databaseAdminRoleDoesNotGrantTenantVisibilityToRuntimeRole() throws Exception {
        try (Connection runtime = runtimeConnection(); Statement statement = runtime.createStatement(); ResultSet result = statement.executeQuery("select rolsuper, rolbypassrls from pg_roles where rolname = current_user")) {
            assertTrue(result.next());
            assertFalse(result.getBoolean(1));
            assertFalse(result.getBoolean(2));
            assertEquals(1, countPlaces(runtime, ORIGINAL_COUPLE));
        }
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
    }

    private static void setCouple(Connection connection, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.couple_id', ?, false)")) {
            statement.setString(1, coupleId.toString());
            statement.execute();
        }
    }

    private static int countPlaces(Connection connection, UUID coupleId) throws Exception {
        if (coupleId != null) setCouple(connection, coupleId);
        return countByName(connection, null);
    }

    private static int countByName(Connection connection, String name) throws Exception {
        String sql = name == null ? "select count(*) from places" : "select count(*) from places where name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (name != null) statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int countByLabel(Connection connection, String label) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from special_dates where label = ?")) {
            statement.setString(1, label);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int countById(Connection connection, String table, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from " + table + " where id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int updateById(Connection connection, String table, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("update " + table + " set id = id where id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        }
    }

    private static int deleteById(Connection connection, String table, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("delete from " + table + " where id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        }
    }

    private static int updatePlace(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("update places set name = 'tampered' where name = ?")) {
            statement.setString(1, name);
            return statement.executeUpdate();
        }
    }

    private static int deletePlace(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("delete from places where name = ?")) {
            statement.setString(1, name);
            return statement.executeUpdate();
        }
    }

    private static void insertPlace(Connection connection, UUID coupleId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into places(name, category_id, created_by, updated_by, couple_id) values ('Unauthorized place', 1, 1, 1, ?)")) {
            statement.setObject(1, coupleId);
            statement.executeUpdate();
        }
    }
}
