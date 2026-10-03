-- Cities retain the legacy zone identifiers for compatible clients.
ALTER TABLE zones ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'AR';
ALTER TABLE zones DROP CONSTRAINT zones_name_key;
CREATE UNIQUE INDEX uq_city_country_name ON zones(country_code, lower(name));
ALTER TABLE couples ADD COLUMN origin_city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
-- The migration role owns these tables but does not bypass FORCE RLS.
-- Owner access is relaxed only inside this migration transaction, then restored.
ALTER TABLE places NO FORCE ROW LEVEL SECURITY;
ALTER TABLE films NO FORCE ROW LEVEL SECURITY;
ALTER TABLE recipes NO FORCE ROW LEVEL SECURITY;
ALTER TABLE why_fun_venues NO FORCE ROW LEVEL SECURITY;
UPDATE places SET zone_id=1;
UPDATE films SET zone_id=1;
UPDATE recipes SET zone_id=1;
UPDATE why_fun_venues SET zone_id=1;
ALTER TABLE places FORCE ROW LEVEL SECURITY;
ALTER TABLE films FORCE ROW LEVEL SECURITY;
ALTER TABLE recipes FORCE ROW LEVEL SECURITY;
ALTER TABLE why_fun_venues FORCE ROW LEVEL SECURITY;
CREATE TABLE journeys (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid REFERENCES couples(id),
 name VARCHAR(160) NOT NULL, starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 archived BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 version BIGINT NOT NULL DEFAULT 0, UNIQUE(id,couple_id), CHECK(ends_on>=starts_on)
);
CREATE TABLE journey_stages (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, city_id BIGINT NOT NULL REFERENCES zones(id), starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 position INTEGER NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,couple_id), UNIQUE(id,journey_id,couple_id), CHECK(ends_on>=starts_on),
 FOREIGN KEY(journey_id,couple_id) REFERENCES journeys(id,couple_id) ON DELETE CASCADE
);
CREATE TABLE journey_points (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, stage_id UUID NOT NULL,
 title VARCHAR(160) NOT NULL, scheduled_on DATE, scheduled_time TIME, notes VARCHAR(4000), maps_url VARCHAR(1000),
 position INTEGER NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', version BIGINT NOT NULL DEFAULT 0,
 place_id BIGINT, film_id BIGINT, recipe_id BIGINT, venue_id BIGINT,
 place_visit_id BIGINT, film_view_id BIGINT, cooking_id BIGINT, fun_visit_id BIGINT,
 UNIQUE(id,couple_id), UNIQUE(id,journey_id,couple_id),
 FOREIGN KEY(stage_id,journey_id,couple_id) REFERENCES journey_stages(id,journey_id,couple_id),
 FOREIGN KEY(place_id,couple_id) REFERENCES places(id,couple_id),
 FOREIGN KEY(film_id,couple_id) REFERENCES films(id,couple_id),
 FOREIGN KEY(recipe_id,couple_id) REFERENCES recipes(id,couple_id),
 FOREIGN KEY(venue_id,couple_id) REFERENCES why_fun_venues(id,couple_id),
 FOREIGN KEY(place_visit_id,couple_id) REFERENCES place_visits(id,couple_id),
 FOREIGN KEY(film_view_id,couple_id) REFERENCES film_views(id,couple_id),
 FOREIGN KEY(cooking_id,couple_id) REFERENCES cookings(id,couple_id),
 FOREIGN KEY(fun_visit_id,couple_id) REFERENCES why_fun_visits(id,couple_id),
 CHECK(status IN ('PENDING','COMPLETED','CANCELLED')),
 CHECK(num_nonnulls(place_id,film_id,recipe_id,venue_id)<=1),
 CHECK(num_nonnulls(place_visit_id,film_view_id,cooking_id,fun_visit_id)<=1),
 CHECK((place_visit_id IS NULL OR place_id IS NOT NULL) AND (film_view_id IS NULL OR film_id IS NOT NULL)
 AND (cooking_id IS NULL OR recipe_id IS NOT NULL) AND (fun_visit_id IS NULL OR venue_id IS NOT NULL))
);
CREATE TABLE journey_stays (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, stage_id UUID NOT NULL, name VARCHAR(160) NOT NULL, starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 address VARCHAR(300), price NUMERIC(18,4), currency VARCHAR(3), source VARCHAR(300), booking_url VARCHAR(1000), maps_url VARCHAR(1000),
 photo_id UUID, version BIGINT NOT NULL DEFAULT 0, UNIQUE(id,couple_id), UNIQUE(id,journey_id,couple_id),
 CHECK(ends_on>=starts_on), CHECK(price IS NULL OR price>=0),
 FOREIGN KEY(stage_id,journey_id,couple_id) REFERENCES journey_stages(id,journey_id,couple_id)
);
CREATE TABLE journey_packing_items (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, user_id BIGINT NOT NULL REFERENCES users(id), description VARCHAR(160) NOT NULL,
 quantity INTEGER NOT NULL DEFAULT 1 CHECK(quantity>0), packed BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,couple_id), FOREIGN KEY(journey_id,couple_id) REFERENCES journeys(id,couple_id) ON DELETE CASCADE
);
CREATE TABLE journey_movements (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, stage_id UUID, point_id UUID, stay_id UUID,
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('FUNDS','EXPENSE','REFUND')),
 description VARCHAR(160) NOT NULL, amount NUMERIC(18,4) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL,
 occurred_on DATE NOT NULL, version BIGINT NOT NULL DEFAULT 0, UNIQUE(id,couple_id), UNIQUE(id,journey_id,couple_id),
 FOREIGN KEY(journey_id,couple_id) REFERENCES journeys(id,couple_id),
 FOREIGN KEY(stage_id,journey_id,couple_id) REFERENCES journey_stages(id,journey_id,couple_id),
 FOREIGN KEY(point_id,journey_id,couple_id) REFERENCES journey_points(id,journey_id,couple_id),
 FOREIGN KEY(stay_id,journey_id,couple_id) REFERENCES journey_stays(id,journey_id,couple_id)
);
CREATE TABLE journey_files (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, stage_id UUID, point_id UUID, stay_id UUID, movement_id UUID,
 name VARCHAR(255) NOT NULL, content_type VARCHAR(80) NOT NULL, byte_size BIGINT NOT NULL,
 content BYTEA NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,couple_id), UNIQUE(id,journey_id,couple_id),
 FOREIGN KEY(journey_id,couple_id) REFERENCES journeys(id,couple_id),
 FOREIGN KEY(stage_id,journey_id,couple_id) REFERENCES journey_stages(id,journey_id,couple_id),
 FOREIGN KEY(point_id,journey_id,couple_id) REFERENCES journey_points(id,journey_id,couple_id),
 FOREIGN KEY(stay_id,journey_id,couple_id) REFERENCES journey_stays(id,journey_id,couple_id),
 FOREIGN KEY(movement_id,journey_id,couple_id) REFERENCES journey_movements(id,journey_id,couple_id)
);
ALTER TABLE journey_stays ADD CONSTRAINT fk_stay_photo FOREIGN KEY(photo_id,journey_id,couple_id) REFERENCES journey_files(id,journey_id,couple_id);
CREATE TABLE journey_reviews (
 id UUID PRIMARY KEY, couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id',true),'')::uuid,
 journey_id UUID NOT NULL, stay_id UUID, user_id BIGINT NOT NULL REFERENCES users(id),
 rating SMALLINT NOT NULL CHECK(rating BETWEEN 1 AND 5), comment VARCHAR(2000), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(journey_id,couple_id) REFERENCES journeys(id,couple_id),
 FOREIGN KEY(stay_id,journey_id,couple_id) REFERENCES journey_stays(id,journey_id,couple_id)
);
CREATE UNIQUE INDEX uq_journey_review ON journey_reviews(journey_id,user_id) WHERE stay_id IS NULL;
CREATE UNIQUE INDEX uq_stay_review ON journey_reviews(stay_id,user_id) WHERE stay_id IS NOT NULL;
ALTER TABLE place_visits ADD COLUMN city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
ALTER TABLE place_visits ADD COLUMN stage_id UUID;
ALTER TABLE place_visits ADD CONSTRAINT fk_place_visits_stage FOREIGN KEY(stage_id,couple_id) REFERENCES journey_stages(id,couple_id);
CREATE INDEX idx_place_visits_location ON place_visits(couple_id,city_id,stage_id);
ALTER TABLE film_views ADD COLUMN city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
ALTER TABLE film_views ADD COLUMN stage_id UUID;
ALTER TABLE film_views ADD CONSTRAINT fk_film_views_stage FOREIGN KEY(stage_id,couple_id) REFERENCES journey_stages(id,couple_id);
CREATE INDEX idx_film_views_location ON film_views(couple_id,city_id,stage_id);
ALTER TABLE cookings ADD COLUMN city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
ALTER TABLE cookings ADD COLUMN stage_id UUID;
ALTER TABLE cookings ADD CONSTRAINT fk_cookings_stage FOREIGN KEY(stage_id,couple_id) REFERENCES journey_stages(id,couple_id);
CREATE INDEX idx_cookings_location ON cookings(couple_id,city_id,stage_id);
ALTER TABLE why_fun_visits ADD COLUMN city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
ALTER TABLE why_fun_visits ADD COLUMN stage_id UUID;
ALTER TABLE why_fun_visits ADD CONSTRAINT fk_why_fun_visits_stage FOREIGN KEY(stage_id,couple_id) REFERENCES journey_stages(id,couple_id);
CREATE INDEX idx_why_fun_visits_location ON why_fun_visits(couple_id,city_id,stage_id);
ALTER TABLE special_date_occurrences ADD COLUMN city_id BIGINT NOT NULL DEFAULT 1 REFERENCES zones(id);
ALTER TABLE special_date_occurrences ADD COLUMN stage_id UUID;
ALTER TABLE special_date_occurrences ADD CONSTRAINT fk_special_date_occurrences_stage FOREIGN KEY(stage_id,couple_id) REFERENCES journey_stages(id,couple_id);
CREATE INDEX idx_special_date_occurrences_location ON special_date_occurrences(couple_id,city_id,stage_id);
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['journeys','journey_stages','journey_points','journey_stays','journey_packing_items','journey_movements','journey_files','journey_reviews'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format($policy$CREATE POLICY %I ON %I USING(couple_id=nullif(current_setting('app.couple_id',true),'')::uuid) WITH CHECK(couple_id=nullif(current_setting('app.couple_id',true),'')::uuid)$policy$, 'policy_'||t||'_couple',t);
  IF t <> 'journeys' THEN EXECUTE format('CREATE INDEX %I ON %I(couple_id,journey_id)', 'idx_'||t||'_couple',t); END IF;
 END LOOP;
END $$;
CREATE INDEX idx_journeys_catalog ON journeys(couple_id,starts_on DESC,id);
CREATE INDEX idx_journey_points_source ON journey_points(couple_id,stage_id,place_id,film_id,recipe_id,venue_id);
CREATE UNIQUE INDEX uq_point_place_visit ON journey_points(couple_id,place_visit_id) WHERE place_visit_id IS NOT NULL;
CREATE UNIQUE INDEX uq_point_film_view ON journey_points(couple_id,film_view_id) WHERE film_view_id IS NOT NULL;
CREATE UNIQUE INDEX uq_point_cooking ON journey_points(couple_id,cooking_id) WHERE cooking_id IS NOT NULL;
CREATE UNIQUE INDEX uq_point_fun_visit ON journey_points(couple_id,fun_visit_id) WHERE fun_visit_id IS NOT NULL;

-- Typed experience references must also match the referenced reusable ficha.
CREATE UNIQUE INDEX uq_journey_place_visit_parent ON place_visits(id,place_id,couple_id);
CREATE UNIQUE INDEX uq_journey_cooking_parent ON cookings(id,recipe_id,couple_id);
CREATE UNIQUE INDEX uq_journey_fun_visit_parent ON why_fun_visits(id,venue_id,couple_id);
ALTER TABLE journey_points ADD CONSTRAINT fk_point_place_experience FOREIGN KEY(place_visit_id,place_id,couple_id) REFERENCES place_visits(id,place_id,couple_id);
ALTER TABLE journey_points ADD CONSTRAINT fk_point_film_experience FOREIGN KEY(film_view_id,film_id,couple_id) REFERENCES film_views(id,film_id,couple_id);
ALTER TABLE journey_points ADD CONSTRAINT fk_point_cooking_experience FOREIGN KEY(cooking_id,recipe_id,couple_id) REFERENCES cookings(id,recipe_id,couple_id);
ALTER TABLE journey_points ADD CONSTRAINT fk_point_fun_experience FOREIGN KEY(fun_visit_id,venue_id,couple_id) REFERENCES why_fun_visits(id,venue_id,couple_id);

-- Immutable membership references retain author identity when members later leave.
CREATE UNIQUE INDEX uq_journey_member_identity ON couple_members(id,couple_id,user_id);
ALTER TABLE journey_packing_items ADD COLUMN member_id BIGINT NOT NULL;
ALTER TABLE journey_reviews ADD COLUMN member_id BIGINT NOT NULL;
ALTER TABLE journey_packing_items ADD CONSTRAINT fk_packing_member FOREIGN KEY(member_id,couple_id,user_id) REFERENCES couple_members(id,couple_id,user_id);
ALTER TABLE journey_reviews ADD CONSTRAINT fk_journey_review_member FOREIGN KEY(member_id,couple_id,user_id) REFERENCES couple_members(id,couple_id,user_id);
