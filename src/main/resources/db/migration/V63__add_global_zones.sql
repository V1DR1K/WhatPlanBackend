CREATE TABLE zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO zones (id, name) VALUES (1, 'Rosario'), (2, 'Buenos Aires');
SELECT setval(pg_get_serial_sequence('zones', 'id'), (SELECT MAX(id) FROM zones));

ALTER TABLE users ADD COLUMN default_zone_id BIGINT DEFAULT 1;
UPDATE users SET default_zone_id = 1 WHERE default_zone_id IS NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_default_zone FOREIGN KEY (default_zone_id) REFERENCES zones(id);
CREATE INDEX idx_users_default_zone ON users(default_zone_id);

ALTER TABLE places ADD COLUMN zone_id BIGINT DEFAULT 1;
ALTER TABLE films ADD COLUMN zone_id BIGINT DEFAULT 1;
ALTER TABLE recipes ADD COLUMN zone_id BIGINT DEFAULT 1;
ALTER TABLE why_fun_venues ADD COLUMN zone_id BIGINT DEFAULT 1;

UPDATE places SET zone_id = 1 WHERE zone_id IS NULL;
UPDATE films SET zone_id = 1 WHERE zone_id IS NULL;
UPDATE recipes SET zone_id = 1 WHERE zone_id IS NULL;
UPDATE why_fun_venues SET zone_id = 1 WHERE zone_id IS NULL;

ALTER TABLE places ALTER COLUMN zone_id SET NOT NULL;
ALTER TABLE films ALTER COLUMN zone_id SET NOT NULL;
ALTER TABLE recipes ALTER COLUMN zone_id SET NOT NULL;
ALTER TABLE why_fun_venues ALTER COLUMN zone_id SET NOT NULL;

ALTER TABLE places ADD CONSTRAINT fk_places_zone FOREIGN KEY (zone_id) REFERENCES zones(id);
ALTER TABLE films ADD CONSTRAINT fk_films_zone FOREIGN KEY (zone_id) REFERENCES zones(id);
ALTER TABLE recipes ADD CONSTRAINT fk_recipes_zone FOREIGN KEY (zone_id) REFERENCES zones(id);
ALTER TABLE why_fun_venues ADD CONSTRAINT fk_why_fun_venues_zone FOREIGN KEY (zone_id) REFERENCES zones(id);

CREATE INDEX idx_places_zone_couple ON places(zone_id, couple_id, id);
CREATE INDEX idx_films_zone_couple ON films(zone_id, couple_id, id);
CREATE INDEX idx_recipes_zone_couple ON recipes(zone_id, couple_id, id);
CREATE INDEX idx_why_fun_venues_zone_couple ON why_fun_venues(zone_id, couple_id, id);
