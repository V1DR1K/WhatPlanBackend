package com.wherefood.couple;

import org.flywaydb.core.Flyway;
import org.postgresql.util.PSQLException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            "special_date_occurrence_comments", "special_date_occurrence_photos", "film_review_metrics", "journeys", "journey_stages", "journey_points", "journey_stays", "journey_files", "journey_movements", "journey_packing_items", "journey_reviews");

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
            statement.executeUpdate("insert into users(username, role) values ('charlie', 'USER'), ('dana', 'USER'), ('erin', 'USER'), ('frank', 'USER'), ('grace', 'USER'), ('hannah', 'USER')");
            statement.executeUpdate("insert into places(name, category_id, created_by, updated_by) values ('Legacy place', 1, 1, 1)");
        }

        Flyway fullFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        fullFlyway.migrate();
        fullFlyway.validate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into couples(id, status, created_by) values ('" + OTHER_COUPLE + "', 'ACTIVE', 1)");
            statement.executeUpdate("insert into places(name, category_id, created_by, updated_by, couple_id) values ('Other couple place', 1, 2, 2, '" + OTHER_COUPLE + "')");
            statement.executeUpdate("insert into special_dates(id, special_date, label, recurrence, couple_id) values (1001, date '2026-01-01', 'Legacy anniversary', 'ONCE', '" + ORIGINAL_COUPLE + "'), (1002, date '2026-01-02', 'Private anniversary', 'ONCE', '" + OTHER_COUPLE + "')");
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
    void mediaQuotaBackfillsLegacyPhotosAndTracksReplacementAndRemoval() throws Exception {
        try (Connection admin = adminConnection()) {
            assertMediaUsage(admin, ORIGINAL_COUPLE, 2L, 1, 536870912L, 2000);

            try (PreparedStatement update = admin.prepareStatement(
                    "update special_date_occurrence_photos set image_base64 = 'larger' where id = 1301")) {
                assertEquals(1, update.executeUpdate());
            }
            assertMediaUsage(admin, ORIGINAL_COUPLE, 7L, 1, 536870912L, 2000);

            try (PreparedStatement delete = admin.prepareStatement(
                    "delete from special_date_occurrence_photos where id = 1301")) {
                assertEquals(1, delete.executeUpdate());
            }
            assertMediaUsage(admin, ORIGINAL_COUPLE, 0L, 0, 536870912L, 2000);
            try (PreparedStatement restore = admin.prepareStatement("""
                    insert into special_date_occurrence_photos
                        (id, occurrence_id, image_base64, thumbnail_base64, width, height, position, created_by, couple_id)
                    values (1301, 1101, 'a', 'a', 1, 1, 0, 1, ?)
                    """)) {
                restore.setObject(1, ORIGINAL_COUPLE);
                restore.executeUpdate();
            }
        }
    }

    @Test
    void concurrentPhotoUploadsCannotExceedCoupleQuota() throws Exception {
        assertConcurrentPhotoQuota(UUID.fromString("00000000-0000-0000-0000-000000000007"),
                9007L, 9107L, 2L, 10);
        assertConcurrentPhotoQuota(UUID.fromString("00000000-0000-0000-0000-000000000008"),
                9008L, 9108L, 100L, 1);
    }

    @Test
    void runtimeRoleCannotReadOrChangeAnotherCouplesRowsAndUnsetContextSeesNothing() throws Exception {
        try (Connection runtime = runtimeConnection()) {
            assertEquals(0, countPlaces(runtime, null), "queries without tenant context must fail closed");
            assertEquals(1, countCoupleFixturePlaces(runtime, ORIGINAL_COUPLE));
            assertEquals(1, countCoupleFixturePlaces(runtime, OTHER_COUPLE));

            setCouple(runtime, ORIGINAL_COUPLE);
            assertEquals(1, countCoupleFixturePlaces(runtime, null));
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
            assertEquals(1, countCoupleFixturePlaces(runtime, ORIGINAL_COUPLE));
        }
    }

    @Test
    void compositeForeignKeysRejectChildRowsWithAnotherCouplesParent() throws Exception {
        try (Connection admin = adminConnection()) {
            long placeId;
            try (PreparedStatement insert = admin.prepareStatement(
                    "insert into places(name, category_id, created_by, updated_by, couple_id) values ('Parent A', 1, 1, 1, ?) returning id")) {
                insert.setObject(1, ORIGINAL_COUPLE);
                try (ResultSet result = insert.executeQuery()) { result.next(); placeId = result.getLong(1); }
            }
            try (PreparedStatement insert = admin.prepareStatement(
                    "insert into place_visits(place_id, visited_on, created_by, updated_by, couple_id) values (?, date '2026-01-03', 2, 2, ?)")) {
                insert.setLong(1, placeId);
                insert.setObject(2, OTHER_COUPLE);
                assertThrows(SQLException.class, insert::executeUpdate,
                        "a child cannot claim a different couple from its parent");
            }
        }
    }

    @Test
    void compositeForeignKeysPreserveExistingCascadeAndCoverPhotoDeleteBehavior() throws Exception {
        try (Connection admin = adminConnection()) {
            long placeId;
            try (PreparedStatement insert = admin.prepareStatement(
                    "insert into places(name, category_id, created_by, updated_by, couple_id) values ('Cascade parent', 1, 1, 1, ?) returning id")) {
                insert.setObject(1, ORIGINAL_COUPLE);
                try (ResultSet result = insert.executeQuery()) { result.next(); placeId = result.getLong(1); }
            }

            long visitId;
            try (PreparedStatement insert = admin.prepareStatement(
                    "insert into place_visits(place_id, visited_on, created_by, updated_by, couple_id) values (?, date '2026-01-04', 1, 1, ?) returning id")) {
                insert.setLong(1, placeId);
                insert.setObject(2, ORIGINAL_COUPLE);
                try (ResultSet result = insert.executeQuery()) { result.next(); visitId = result.getLong(1); }
            }

            long photoId;
            try (PreparedStatement insert = admin.prepareStatement(
                    "insert into place_visit_photos(visit_id, image_base64, thumbnail_base64, width, height, position, created_by, couple_id) values (?, 'a', 'a', 1, 1, 0, 1, ?) returning id")) {
                insert.setLong(1, visitId);
                insert.setObject(2, ORIGINAL_COUPLE);
                try (ResultSet result = insert.executeQuery()) { result.next(); photoId = result.getLong(1); }
            }
            try (PreparedStatement update = admin.prepareStatement("update place_visits set cover_photo_id = ? where id = ?")) {
                update.setLong(1, photoId);
                update.setLong(2, visitId);
                assertEquals(1, update.executeUpdate());
            }
            try (PreparedStatement delete = admin.prepareStatement("delete from place_visit_photos where id = ?")) {
                delete.setLong(1, photoId);
                assertEquals(1, delete.executeUpdate());
            }
            try (PreparedStatement query = admin.prepareStatement("select cover_photo_id from place_visits where id = ?")) {
                query.setLong(1, visitId);
                try (ResultSet result = query.executeQuery()) { assertTrue(result.next()); assertNull(result.getObject(1)); }
            }

            try (PreparedStatement delete = admin.prepareStatement("delete from places where id = ?")) {
                delete.setLong(1, placeId);
                assertEquals(1, delete.executeUpdate());
            }
            try (PreparedStatement query = admin.prepareStatement("select count(*) from place_visits where id = ?")) {
                query.setLong(1, visitId);
                try (ResultSet result = query.executeQuery()) { result.next(); assertEquals(0, result.getInt(1)); }
            }
        }
    }

    @Test
    void concurrentAcceptancesForOneRemainingSlotCannotCreateAThirdMember() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        long ownerId = createTestUser("accept-owner");
        long firstCandidateId = createTestUser("accept-first");
        long secondCandidateId = createTestUser("accept-second");
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into couples(id, status, created_by) values ('" + coupleId + "', 'PENDING', " + ownerId + ")");
        }
        try (Connection admin = adminConnection(); PreparedStatement member = admin.prepareStatement(
                "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Owner', 1, 'ACTIVE')")) {
            member.setObject(1, coupleId); member.setLong(2, ownerId); member.executeUpdate();
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> dana = executor.submit(() -> tryJoin(coupleId, firstCandidateId, start));
            Future<Boolean> erin = executor.submit(() -> tryJoin(coupleId, secondCandidateId, start));
            start.countDown();
            assertEquals(1, (dana.get() ? 1 : 0) + (erin.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }

        try (Connection admin = adminConnection(); PreparedStatement count = admin.prepareStatement(
                "select count(*) from couple_members where couple_id = ? and status = 'ACTIVE'")) {
            count.setObject(1, coupleId);
            try (ResultSet result = count.executeQuery()) { result.next(); assertEquals(2, result.getInt(1)); }
        }
    }

    @Test
    void concurrentPairCreationForOneUserCreatesOnlyOneActiveMembership() throws Exception {
        UUID firstCouple = UUID.randomUUID();
        UUID secondCouple = UUID.randomUUID();
        long userId = createTestUser("paircreator");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryCreatePair(userId, firstCouple, start));
            Future<Boolean> second = executor.submit(() -> tryCreatePair(userId, secondCouple, start));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "select count(*) from couple_members where user_id = ? and status = 'ACTIVE'")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) { result.next(); assertEquals(1, result.getInt(1)); }
        }
    }

    @Test
    void concurrentPendingInvitationsAreLimitedToOnePerCouple() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into couples(id, status, created_by) values ('" + coupleId + "', 'PENDING', 3)");
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryCreateInvitation(coupleId, "first", start));
            Future<Boolean> second = executor.submit(() -> tryCreateInvitation(coupleId, "second", start));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentInvitationAcceptanceAndRevocationHaveExactlyOneWinner() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        long invitationId;
        long ownerId = createTestUser("inviteowner");
        long joinerId = createTestUser("invitejoiner");
        try (Connection admin = adminConnection(); PreparedStatement insert = admin.prepareStatement(
                "insert into couples(id, status, created_by) values (?, 'PENDING', ?)")) {
            insert.setObject(1, coupleId);
            insert.setLong(2, ownerId);
            insert.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement insert = admin.prepareStatement(
                "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Owner', 1, 'ACTIVE')")) {
            insert.setObject(1, coupleId);
            insert.setLong(2, ownerId);
            insert.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement insert = admin.prepareStatement(
                "insert into couple_invitations(couple_id, created_by, token_hash, expires_at) values (?, ?, ?, now() + interval '7 days') returning id")) {
            insert.setObject(1, coupleId);
            insert.setLong(2, ownerId);
            insert.setString(3, "c".repeat(64));
            try (ResultSet result = insert.executeQuery()) { result.next(); invitationId = result.getLong(1); }
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> accepted = executor.submit(() -> tryFinalizeInvitation(coupleId, invitationId, joinerId, true, start));
            Future<Boolean> revoked = executor.submit(() -> tryFinalizeInvitation(coupleId, invitationId, joinerId, false, start));
            start.countDown();
            assertEquals(1, (accepted.get() ? 1 : 0) + (revoked.get() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }

        try (Connection admin = adminConnection(); PreparedStatement invitation = admin.prepareStatement(
                "select status from couple_invitations where id = ?")) {
            invitation.setLong(1, invitationId);
            try (ResultSet result = invitation.executeQuery()) {
                assertTrue(result.next());
                String status = result.getString(1);
                assertTrue(status.equals("ACCEPTED") || status.equals("REVOKED"));
                assertEquals(status.equals("ACCEPTED") ? 2 : 1, countActiveMembers(admin, coupleId));
            }
        }
    }

    @Test
    void leftMembershipKeepsHistoryButReleasesTheSlotAndUserForANewPair() throws Exception {
        UUID coupleId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        long formerMemberId = createTestUser("former-member");
        long newMemberId = createTestUser("new-member");
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.executeUpdate("insert into couples(id, status, created_by) values ('" + coupleId + "', 'ACTIVE', " + formerMemberId + ")");
        }
        try (Connection admin = adminConnection(); PreparedStatement member = admin.prepareStatement(
                "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Former', 1, 'ACTIVE')")) {
            member.setObject(1, coupleId); member.setLong(2, formerMemberId); member.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement leave = admin.prepareStatement(
                "update couple_members set status = 'LEFT', left_at = now() where couple_id = ? and user_id = ?")) {
            leave.setObject(1, coupleId); leave.setLong(2, formerMemberId); leave.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement member = admin.prepareStatement(
                "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'New', 1, 'ACTIVE')")) {
            member.setObject(1, coupleId); member.setLong(2, newMemberId); member.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement member = admin.prepareStatement(
                "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Former', 1, 'ACTIVE')")) {
            member.setObject(1, OTHER_COUPLE); member.setLong(2, formerMemberId); member.executeUpdate();
        }
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "select count(*) from couple_members where couple_id = ? and user_id = ? and status = 'LEFT'")) {
            statement.setObject(1, coupleId);
            statement.setLong(2, formerMemberId);
            try (ResultSet result = statement.executeQuery()) { result.next(); assertEquals(1, result.getInt(1)); }
        }
    }

    private static boolean tryJoin(UUID coupleId, long userId, CountDownLatch start) throws Exception {
        start.await();
        try (Connection connection = adminConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("select id from couples where id = ? for update")) {
                    lock.setObject(1, coupleId);
                    try (ResultSet result = lock.executeQuery()) { if (!result.next()) throw new SQLException("couple missing"); }
                }
                int active = 0;
                boolean slotOneTaken = false;
                try (PreparedStatement members = connection.prepareStatement(
                        "select slot from couple_members where couple_id = ? and status = 'ACTIVE'")) {
                    members.setObject(1, coupleId);
                    try (ResultSet result = members.executeQuery()) {
                        while (result.next()) { active++; if (result.getShort(1) == 1) slotOneTaken = true; }
                    }
                }
                if (active >= 2) { connection.commit(); return false; }
                short slot = slotOneTaken ? (short) 2 : (short) 1;
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, ?, ?, 'ACTIVE')")) {
                    insert.setObject(1, coupleId); insert.setLong(2, userId);
                    insert.setString(3, userId == 4 ? "Dana" : "Erin"); insert.setShort(4, slot); insert.executeUpdate();
                }
                if (active == 1) {
                    try (PreparedStatement activate = connection.prepareStatement("update couples set status = 'ACTIVE' where id = ?")) {
                        activate.setObject(1, coupleId); activate.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static boolean tryCreateInvitation(UUID coupleId, String suffix, CountDownLatch start) throws Exception {
        start.await();
        try (Connection connection = adminConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into couple_invitations(couple_id, created_by, token_hash, expires_at) values (?, 3, ?, now() + interval '7 days')")) {
                insert.setObject(1, coupleId); insert.setString(2, String.format("%064x", (long) suffix.hashCode() & 0xffffffffL));
                insert.executeUpdate(); connection.commit(); return true;
            } catch (SQLException conflict) {
                connection.rollback(); return false;
            }
        }
    }

    private static boolean tryFinalizeInvitation(UUID coupleId, long invitationId, long joinerId, boolean accept, CountDownLatch start) throws Exception {
        start.await();
        try (Connection connection = adminConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("select id from couples where id = ? for update")) {
                    lock.setObject(1, coupleId);
                    try (ResultSet result = lock.executeQuery()) { if (!result.next()) throw new SQLException("couple missing"); }
                }
                String status;
                try (PreparedStatement invitation = connection.prepareStatement("select status from couple_invitations where id = ? for update")) {
                    invitation.setLong(1, invitationId);
                    try (ResultSet result = invitation.executeQuery()) { if (!result.next()) throw new SQLException("invitation missing"); status = result.getString(1); }
                }
                if (!"PENDING".equals(status)) { connection.commit(); return false; }
                if (accept) {
                    try (PreparedStatement update = connection.prepareStatement("update couple_invitations set status = 'ACCEPTED', accepted_by = ?, accepted_at = now() where id = ?")) {
                        update.setLong(1, joinerId); update.setLong(2, invitationId); update.executeUpdate();
                    }
                    try (PreparedStatement member = connection.prepareStatement("insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Joiner', 2, 'ACTIVE')")) {
                        member.setObject(1, coupleId); member.setLong(2, joinerId); member.executeUpdate();
                    }
                    try (PreparedStatement couple = connection.prepareStatement("update couples set status = 'ACTIVE' where id = ?")) {
                        couple.setObject(1, coupleId); couple.executeUpdate();
                    }
                } else {
                    try (PreparedStatement update = connection.prepareStatement("update couple_invitations set status = 'REVOKED', revoked_at = now() where id = ?")) {
                        update.setLong(1, invitationId); update.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static int countActiveMembers(Connection connection, UUID coupleId) throws SQLException {
        try (PreparedStatement count = connection.prepareStatement("select count(*) from couple_members where couple_id = ? and status = 'ACTIVE'")) {
            count.setObject(1, coupleId);
            try (ResultSet result = count.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    private static boolean tryInsertQuotaPhoto(UUID coupleId, long occurrenceId, long userId, int position,
            CountDownLatch start) throws Exception {
        start.await();
        try (Connection connection = adminConnection(); PreparedStatement insert = connection.prepareStatement(
                "insert into special_date_occurrence_photos(occurrence_id, image_base64, thumbnail_base64, width, height, position, created_by, couple_id) values (?, 'a', 'a', 1, 1, ?, ?, ?)")) {
            insert.setLong(1, occurrenceId);
            insert.setInt(2, position);
            insert.setLong(3, userId);
            insert.setObject(4, coupleId);
            insert.executeUpdate();
            return true;
        } catch (SQLException quotaExceeded) {
            if (!(quotaExceeded instanceof PSQLException postgresError)
                    || !"23514".equals(postgresError.getSQLState())
                    || postgresError.getServerErrorMessage() == null
                    || !"chk_couples_media_quota".equals(postgresError.getServerErrorMessage().getConstraint())) {
                throw quotaExceeded;
            }
            return false;
        }
    }

    private static void assertConcurrentPhotoQuota(UUID coupleId, long dateId, long occurrenceId,
            long quotaBytes, int photoQuota) throws Exception {
        try (Connection admin = adminConnection()) {
            try (PreparedStatement couple = admin.prepareStatement(
                    "insert into couples(id, status, created_by, media_quota_bytes, media_quota_photos) values (?, 'PENDING', 3, ?, ?)")) {
                couple.setObject(1, coupleId);
                couple.setLong(2, quotaBytes);
                couple.setInt(3, photoQuota);
                couple.executeUpdate();
            }
            try (PreparedStatement date = admin.prepareStatement(
                    "insert into special_dates(id, special_date, label, recurrence, couple_id) values (?, date '2026-09-25', ?, 'ONCE', ?)")) {
                date.setLong(1, dateId);
                date.setString(2, "Quota test " + dateId);
                date.setObject(3, coupleId);
                date.executeUpdate();
            }
            try (PreparedStatement occurrence = admin.prepareStatement(
                    "insert into special_date_occurrences(id, special_date_id, occurred_on, created_by, updated_by, couple_id) values (?, ?, date '2026-09-25', 3, 3, ?)")) {
                occurrence.setLong(1, occurrenceId);
                occurrence.setLong(2, dateId);
                occurrence.setObject(3, coupleId);
                occurrence.executeUpdate();
            }
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryInsertQuotaPhoto(coupleId, occurrenceId, 3L, 1, start));
            Future<Boolean> second = executor.submit(() -> tryInsertQuotaPhoto(coupleId, occurrenceId, 3L, 2, start));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0),
                    "Exactly one concurrent insert must be rejected by the media quota trigger");
        } finally {
            executor.shutdownNow();
        }

        try (Connection admin = adminConnection()) {
            assertMediaUsage(admin, coupleId, 2L, 1, quotaBytes, photoQuota);
        }
    }

    private static void assertMediaUsage(Connection connection, UUID coupleId, long usedBytes, int photoCount,
            long quotaBytes, int photoQuota) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select media_used_bytes, media_photo_count, media_quota_bytes, media_quota_photos from couples where id = ?")) {
            statement.setObject(1, coupleId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(usedBytes, result.getLong(1));
                assertEquals(photoCount, result.getInt(2));
                assertEquals(quotaBytes, result.getLong(3));
                assertEquals(photoQuota, result.getInt(4));
            }
        }
    }

    private static boolean tryCreatePair(long userId, UUID coupleId, CountDownLatch start) throws Exception {
        start.await();
        try (Connection connection = adminConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("select id from users where id = ? for update")) {
                    lock.setLong(1, userId);
                    try (ResultSet result = lock.executeQuery()) { if (!result.next()) throw new SQLException("user missing"); }
                }
                try (PreparedStatement existing = connection.prepareStatement(
                        "select count(*) from couple_members where user_id = ? and status = 'ACTIVE'")) {
                    existing.setLong(1, userId);
                    try (ResultSet result = existing.executeQuery()) {
                        result.next(); if (result.getInt(1) > 0) { connection.commit(); return false; }
                    }
                }
                try (PreparedStatement couple = connection.prepareStatement(
                        "insert into couples(id, status, created_by) values (?, 'PENDING', ?)")) {
                    couple.setObject(1, coupleId); couple.setLong(2, userId); couple.executeUpdate();
                }
                try (PreparedStatement member = connection.prepareStatement(
                        "insert into couple_members(couple_id, user_id, display_name, slot, status) values (?, ?, 'Hannah', 1, 'ACTIVE')")) {
                    member.setObject(1, coupleId); member.setLong(2, userId); member.executeUpdate();
                }
                connection.commit(); return true;
            } catch (SQLException exception) {
                connection.rollback(); throw exception;
            }
        }
    }

    private static long createTestUser(String prefix) throws Exception {
        try (Connection connection = adminConnection(); PreparedStatement insert = connection.prepareStatement(
                "insert into users(username, role) values (?, 'USER') returning id")) {
            insert.setString(1, prefix + "-" + UUID.randomUUID());
            try (ResultSet result = insert.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }

    private static int countCoupleFixturePlaces(Connection connection, UUID coupleId) throws Exception {
        if (coupleId != null) setCouple(connection, coupleId);
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from places where name in ('Legacy place', 'Other couple place')")) {
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
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
