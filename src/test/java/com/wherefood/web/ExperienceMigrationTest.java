package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExperienceMigrationTest {
 @Test
 void foodMigrationProtectsDeletedLegacyDataAndMapsTheSpecifiedVisit() throws IOException {
  String sql = migration("V27__unify_place_visit_experiences.sql");
  assertTrue(sql.contains("item.deleted_at is null and item.id <> 6"));
  assertTrue(sql.contains("Un Churrito Rosario"));
  assertTrue(sql.contains("date '2026-07-17'"));
  assertTrue(sql.contains("round(avg(metric), 0)::smallint as overall"));
 }

 @Test
  void laterMigrationsCreateChildExperienceTables() throws IOException {
  assertTrue(migration("V28__split_why_fun_activities_and_visits.sql").contains("create table why_fun_visits"));
  assertTrue(migration("V29__add_film_view_photos.sql").contains("create table film_view_photos"));
   assertTrue(migration("V30__split_recipes_and_cookings.sql").contains("create table cookings"));
  }

  @Test
  void restoresParentProfilesWithoutDeletingLegacySourceTables() throws IOException {
   String sql = migration("V31__restore_parent_profile_media.sql");
   assertTrue(sql.contains("create table recipe_photos"));
   assertTrue(sql.contains("join home_recipe_photos"));
   assertTrue(sql.contains("insert into film_photos"));
   assertTrue(sql.contains("delete from film_view_photos"));
   assertTrue(sql.contains("update place_visits visit"));
   assertTrue(sql.contains("update why_fun_visits visit"));
   assertTrue(!sql.contains("delete from place_photos"));
   assertTrue(!sql.contains("delete from home_recipe_photos"));
   assertTrue(!sql.contains("delete from why_fun_venue_photos"));
  }

  @Test
  void simplifiesExperiencesWithoutDiscardingTheOnlyProfileImage() throws IOException {
   String sql = migration("V32__simplify_experiences_and_reviews.sql");
   assertTrue(sql.contains("alter table film_views drop column watched_at"));
   assertTrue(sql.contains("drop table film_view_photos"));
   assertTrue(sql.contains("drop table cooking_photos"));
   assertTrue(sql.contains("alter table why_fun_visits alter column scheduled_at type date"));
   assertTrue(sql.contains("alter table place_visit_reviews"));
  }

  @Test
  void createsAReusableSpecialDatesCalendar() throws IOException {
   String sql = migration("V34__add_special_dates.sql");
   assertTrue(sql.contains("create table special_dates"));
   assertTrue(sql.contains("special_date date not null"));
  }

  @Test
  void createsAndSeedsTheSingletonGlobalSettings() throws IOException {
   String sql = migration("V35__add_global_settings.sql");
   assertTrue(sql.contains("create table global_settings"));
   assertTrue(sql.contains("catalog_page_size integer not null default 5"));
   assertTrue(sql.contains("catalog_page_size between 1 and 50"));
   assertTrue(sql.contains("on conflict (id) do nothing"));
  }

  @Test
  void alignsTheGlobalSettingsIdentifierWithItsEntity() throws IOException {
   assertTrue(migration("V36__fix_global_settings_id_type.sql").contains("alter column id type integer"));
  }

  @Test
  void makesSpecialDatesRecurringAndSeedsTheDefaultCalendar() throws IOException {
   String sql = migration("V37__add_special_date_recurrence.sql");
   assertTrue(sql.contains("add column recurrence varchar(16)"));
   assertTrue(sql.contains("set recurrence = 'ONCE'"));
   assertTrue(sql.contains("chk_special_dates_recurrence"));
   assertTrue(sql.contains("('ONCE', 'ANNUAL', 'MONTHLY')"));
   assertTrue(sql.contains("where not exists"));
   assertEquals(8, sql.split("date '", -1).length - 1);
   assertTrue(sql.contains("(date '2026-02-14', 'San Valentín', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2026-10-03', 'Día del Novio', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2026-06-27', 'Nuestro aniversario', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2026-06-27', 'Mensuario', 'MONTHLY')"));
   assertTrue(sql.contains("(date '2026-05-03', 'Primera vez que hablamos', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2026-05-09', 'Primera cita', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2005-04-12', 'Cumpleaños de Tomás', 'ANNUAL')"));
   assertTrue(sql.contains("(date '2004-04-12', 'Cumpleaños de Avril', 'ANNUAL')"));
  }

 private static String migration(String name) throws IOException {
  try (InputStream stream = ExperienceMigrationTest.class.getResourceAsStream("/db/migration/" + name)) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
 }
}
