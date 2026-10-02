-- A couple can have no more than two ACTIVE members (slots 1 and 2), while
-- historical LEFT rows may retain their former slot and a replacement may use it.
ALTER TABLE couple_members DROP CONSTRAINT couple_members_couple_id_user_id_key;
ALTER TABLE couple_members DROP CONSTRAINT couple_members_couple_id_slot_key;
ALTER TABLE couple_members ADD CONSTRAINT chk_couple_member_lifecycle
  CHECK ((status = 'ACTIVE' AND left_at IS NULL) OR (status = 'LEFT' AND left_at IS NOT NULL AND left_at >= joined_at));
CREATE UNIQUE INDEX uq_couple_members_active_pair ON couple_members(couple_id, user_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_couple_members_active_slot ON couple_members(couple_id, slot) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_couple_invitations_pending_couple ON couple_invitations(couple_id) WHERE status = 'PENDING';
ALTER TABLE couple_invitations ADD CONSTRAINT chk_couple_invitation_lifecycle CHECK (
  (status = 'ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL AND revoked_at IS NULL)
  OR (status = 'REVOKED' AND accepted_by IS NULL AND accepted_at IS NULL AND revoked_at IS NOT NULL)
  OR (status IN ('PENDING', 'EXPIRED') AND accepted_by IS NULL AND accepted_at IS NULL AND revoked_at IS NULL)
);
ALTER TABLE couples ADD CONSTRAINT chk_closed_couple_timestamp
  CHECK ((status = 'CLOSED' AND closed_at IS NOT NULL) OR (status <> 'CLOSED' AND closed_at IS NULL));

-- This legacy table was omitted from V45's tenant list. Derive its tenant only
-- from its already-backfilled private parent, never from a caller-supplied value.
ALTER TABLE film_review_metrics ADD COLUMN couple_id uuid;
UPDATE film_review_metrics child SET couple_id = parent.couple_id
  FROM film_reviews parent WHERE parent.id = child.review_id;
ALTER TABLE film_review_metrics ALTER COLUMN couple_id SET NOT NULL;
ALTER TABLE film_review_metrics ALTER COLUMN couple_id SET DEFAULT nullif(current_setting('app.couple_id', true), '')::uuid;
ALTER TABLE film_review_metrics ADD CONSTRAINT fk_film_review_metrics_couple FOREIGN KEY (couple_id) REFERENCES couples(id);
CREATE INDEX idx_film_review_metrics_couple_review ON film_review_metrics(couple_id, review_id);
ALTER TABLE film_review_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE film_review_metrics FORCE ROW LEVEL SECURITY;
CREATE POLICY policy_film_review_metrics_couple ON film_review_metrics
  USING (couple_id = nullif(current_setting('app.couple_id', true), '')::uuid)
  WITH CHECK (couple_id = nullif(current_setting('app.couple_id', true), '')::uuid);

-- Composite parent keys let PostgreSQL reject a child whose couple_id differs
-- from its parent, even for privileged application code that bypasses services.
CREATE UNIQUE INDEX uq_places_id_couple ON places(id, couple_id);
CREATE UNIQUE INDEX uq_place_visits_id_couple ON place_visits(id, couple_id);
CREATE UNIQUE INDEX uq_items_id_couple ON items(id, couple_id);
CREATE UNIQUE INDEX uq_place_visit_photos_parent ON place_visit_photos(id, visit_id, couple_id);
CREATE UNIQUE INDEX uq_films_id_couple ON films(id, couple_id);
CREATE UNIQUE INDEX uq_film_views_id_couple ON film_views(id, couple_id);
CREATE UNIQUE INDEX uq_film_views_parent ON film_views(id, film_id, couple_id);
CREATE UNIQUE INDEX uq_film_reviews_id_couple ON film_reviews(id, couple_id);
CREATE UNIQUE INDEX uq_recipes_id_couple ON recipes(id, couple_id);
CREATE UNIQUE INDEX uq_cookings_id_couple ON cookings(id, couple_id);
CREATE UNIQUE INDEX uq_home_recipes_id_couple ON home_recipes(id, couple_id);
CREATE UNIQUE INDEX uq_why_fun_venues_id_couple ON why_fun_venues(id, couple_id);
CREATE UNIQUE INDEX uq_why_fun_venue_photos_parent ON why_fun_venue_photos(id, venue_id, couple_id);
CREATE UNIQUE INDEX uq_why_fun_visits_id_couple ON why_fun_visits(id, couple_id);
CREATE UNIQUE INDEX uq_why_fun_visit_photos_parent ON why_fun_visit_photos(id, visit_id, couple_id);
CREATE UNIQUE INDEX uq_special_dates_id_couple ON special_dates(id, couple_id);
CREATE UNIQUE INDEX uq_special_occurrences_id_couple ON special_date_occurrences(id, couple_id);
CREATE UNIQUE INDEX uq_special_photos_parent ON special_date_occurrence_photos(id, occurrence_id, couple_id);

ALTER TABLE place_visits ADD CONSTRAINT fk_place_visits_place_couple FOREIGN KEY (place_id, couple_id) REFERENCES places(id, couple_id) ON DELETE CASCADE;
ALTER TABLE items ADD CONSTRAINT fk_items_visit_couple FOREIGN KEY (visit_id, couple_id) REFERENCES place_visits(id, couple_id) ON DELETE CASCADE;
ALTER TABLE item_photos ADD CONSTRAINT fk_item_photos_item_couple FOREIGN KEY (item_id, couple_id) REFERENCES items(id, couple_id) ON DELETE CASCADE;
ALTER TABLE item_reviews ADD CONSTRAINT fk_item_reviews_item_couple FOREIGN KEY (item_id, couple_id) REFERENCES items(id, couple_id) ON DELETE CASCADE;
ALTER TABLE place_photos ADD CONSTRAINT fk_place_photos_place_couple FOREIGN KEY (place_id, couple_id) REFERENCES places(id, couple_id) ON DELETE CASCADE;
ALTER TABLE place_reviews ADD CONSTRAINT fk_place_reviews_place_couple FOREIGN KEY (place_id, couple_id) REFERENCES places(id, couple_id) ON DELETE CASCADE;
ALTER TABLE place_visit_photos ADD CONSTRAINT fk_pv_photos_visit_couple FOREIGN KEY (visit_id, couple_id) REFERENCES place_visits(id, couple_id) ON DELETE CASCADE;
ALTER TABLE place_visit_reviews ADD CONSTRAINT fk_pv_reviews_visit_couple FOREIGN KEY (visit_id, couple_id) REFERENCES place_visits(id, couple_id) ON DELETE CASCADE;
ALTER TABLE place_highlight_tags ADD CONSTRAINT fk_place_tags_place_couple FOREIGN KEY (place_id, couple_id) REFERENCES places(id, couple_id) ON DELETE CASCADE;

ALTER TABLE film_photos ADD CONSTRAINT fk_film_photos_film_couple FOREIGN KEY (film_id, couple_id) REFERENCES films(id, couple_id) ON DELETE CASCADE;
ALTER TABLE film_reviews ADD CONSTRAINT fk_film_reviews_film_couple FOREIGN KEY (film_id, couple_id) REFERENCES films(id, couple_id) ON DELETE CASCADE;
ALTER TABLE film_views ADD CONSTRAINT fk_film_views_film_couple FOREIGN KEY (film_id, couple_id) REFERENCES films(id, couple_id) ON DELETE CASCADE;
ALTER TABLE film_genres ADD CONSTRAINT fk_film_genres_film_couple FOREIGN KEY (film_id, couple_id) REFERENCES films(id, couple_id) ON DELETE CASCADE;
ALTER TABLE film_reviews ADD CONSTRAINT fk_film_reviews_view_film_couple
  FOREIGN KEY (view_id, film_id, couple_id) REFERENCES film_views(id, film_id, couple_id) ON DELETE CASCADE;
ALTER TABLE film_review_metrics ADD CONSTRAINT fk_film_metrics_review_couple FOREIGN KEY (review_id, couple_id) REFERENCES film_reviews(id, couple_id) ON DELETE CASCADE;

ALTER TABLE recipe_ingredients ADD CONSTRAINT fk_recipe_ingredients_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE recipe_steps ADD CONSTRAINT fk_recipe_steps_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE recipe_photos ADD CONSTRAINT fk_recipe_photos_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE cookings ADD CONSTRAINT fk_cookings_recipe_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES recipes(id, couple_id) ON DELETE RESTRICT;
ALTER TABLE cooking_reviews ADD CONSTRAINT fk_cooking_reviews_parent_couple FOREIGN KEY (cooking_id, couple_id) REFERENCES cookings(id, couple_id) ON DELETE CASCADE;

ALTER TABLE home_recipe_ingredients ADD CONSTRAINT fk_home_recipe_ingredients_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES home_recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE home_recipe_steps ADD CONSTRAINT fk_home_recipe_steps_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES home_recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE home_recipe_photos ADD CONSTRAINT fk_home_recipe_photos_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES home_recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE home_recipe_reviews ADD CONSTRAINT fk_home_recipe_reviews_parent_couple FOREIGN KEY (recipe_id, couple_id) REFERENCES home_recipes(id, couple_id) ON DELETE CASCADE;
ALTER TABLE home_recipes ADD CONSTRAINT fk_home_recipes_repeat_same_couple FOREIGN KEY (repeated_from_id, couple_id) REFERENCES home_recipes(id, couple_id);

ALTER TABLE why_fun_venue_schedules ADD CONSTRAINT fk_venue_schedules_parent_couple FOREIGN KEY (venue_id, couple_id) REFERENCES why_fun_venues(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_venue_photos ADD CONSTRAINT fk_venue_photos_parent_couple FOREIGN KEY (venue_id, couple_id) REFERENCES why_fun_venues(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_venue_reviews ADD CONSTRAINT fk_venue_reviews_parent_couple FOREIGN KEY (venue_id, couple_id) REFERENCES why_fun_venues(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_visits ADD CONSTRAINT fk_why_fun_visits_venue_couple FOREIGN KEY (venue_id, couple_id) REFERENCES why_fun_venues(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_visit_photos ADD CONSTRAINT fk_why_fun_visit_photos_parent_couple FOREIGN KEY (visit_id, couple_id) REFERENCES why_fun_visits(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_visit_reviews ADD CONSTRAINT fk_why_fun_visit_reviews_parent_couple FOREIGN KEY (visit_id, couple_id) REFERENCES why_fun_visits(id, couple_id) ON DELETE CASCADE;
ALTER TABLE why_fun_venues ADD CONSTRAINT fk_venue_cover_photo_parent_couple
  FOREIGN KEY (cover_photo_id, id, couple_id) REFERENCES why_fun_venue_photos(id, venue_id, couple_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE why_fun_visits ADD CONSTRAINT fk_why_fun_visit_cover_photo_parent_couple
  FOREIGN KEY (cover_photo_id, id, couple_id) REFERENCES why_fun_visit_photos(id, visit_id, couple_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE special_date_occurrences ADD CONSTRAINT fk_occurrences_date_couple FOREIGN KEY (special_date_id, couple_id) REFERENCES special_dates(id, couple_id) ON DELETE CASCADE;
ALTER TABLE special_date_occurrence_comments ADD CONSTRAINT fk_occ_comments_parent_couple FOREIGN KEY (occurrence_id, couple_id) REFERENCES special_date_occurrences(id, couple_id) ON DELETE CASCADE;
ALTER TABLE special_date_occurrence_photos ADD CONSTRAINT fk_occ_photos_parent_couple FOREIGN KEY (occurrence_id, couple_id) REFERENCES special_date_occurrences(id, couple_id) ON DELETE CASCADE;
ALTER TABLE special_date_occurrences ADD CONSTRAINT fk_occurrences_cover_photo_parent_couple
  FOREIGN KEY (cover_photo_id, id, couple_id) REFERENCES special_date_occurrence_photos(id, occurrence_id, couple_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE place_visits ADD CONSTRAINT fk_place_visits_cover_photo_parent_couple
  FOREIGN KEY (cover_photo_id, id, couple_id) REFERENCES place_visit_photos(id, visit_id, couple_id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_place_visits_couple_place ON place_visits(couple_id, place_id);
CREATE INDEX idx_items_couple_visit ON items(couple_id, visit_id);
CREATE INDEX idx_item_photos_couple_item ON item_photos(couple_id, item_id);
CREATE INDEX idx_item_reviews_couple_item ON item_reviews(couple_id, item_id);
CREATE INDEX idx_place_photos_couple_place ON place_photos(couple_id, place_id);
CREATE INDEX idx_place_reviews_couple_place ON place_reviews(couple_id, place_id);
CREATE INDEX idx_pv_photos_couple_visit ON place_visit_photos(couple_id, visit_id);
CREATE INDEX idx_pv_reviews_couple_visit ON place_visit_reviews(couple_id, visit_id);
CREATE INDEX idx_films_couple_tmdb ON films(couple_id, tmdb_id);
CREATE INDEX idx_film_photos_couple_film ON film_photos(couple_id, film_id);
CREATE INDEX idx_film_reviews_couple_film ON film_reviews(couple_id, film_id);
CREATE INDEX idx_film_views_couple_film ON film_views(couple_id, film_id);
CREATE INDEX idx_film_genres_couple_film ON film_genres(couple_id, film_id);
CREATE INDEX idx_recipe_ingredients_couple_recipe ON recipe_ingredients(couple_id, recipe_id);
CREATE INDEX idx_recipe_steps_couple_recipe ON recipe_steps(couple_id, recipe_id);
CREATE INDEX idx_recipe_photos_couple_recipe ON recipe_photos(couple_id, recipe_id);
CREATE INDEX idx_cookings_couple_recipe ON cookings(couple_id, recipe_id);
CREATE INDEX idx_cooking_reviews_couple_cooking ON cooking_reviews(couple_id, cooking_id);
CREATE INDEX idx_home_recipe_ingredients_couple_recipe ON home_recipe_ingredients(couple_id, recipe_id);
CREATE INDEX idx_home_recipe_steps_couple_recipe ON home_recipe_steps(couple_id, recipe_id);
CREATE INDEX idx_home_recipe_photos_couple_recipe ON home_recipe_photos(couple_id, recipe_id);
CREATE INDEX idx_home_recipe_reviews_couple_recipe ON home_recipe_reviews(couple_id, recipe_id);
CREATE INDEX idx_venue_schedules_couple_venue ON why_fun_venue_schedules(couple_id, venue_id);
CREATE INDEX idx_venue_photos_couple_venue ON why_fun_venue_photos(couple_id, venue_id);
CREATE INDEX idx_venue_reviews_couple_venue ON why_fun_venue_reviews(couple_id, venue_id);
CREATE INDEX idx_fun_visits_couple_venue ON why_fun_visits(couple_id, venue_id);
CREATE INDEX idx_fun_visit_photos_couple_visit ON why_fun_visit_photos(couple_id, visit_id);
CREATE INDEX idx_fun_visit_reviews_couple_visit ON why_fun_visit_reviews(couple_id, visit_id);
CREATE INDEX idx_occurrences_couple_date ON special_date_occurrences(couple_id, special_date_id);
CREATE INDEX idx_occ_comments_couple_occurrence ON special_date_occurrence_comments(couple_id, occurrence_id);
CREATE INDEX idx_occ_photos_couple_occurrence ON special_date_occurrence_photos(couple_id, occurrence_id);
