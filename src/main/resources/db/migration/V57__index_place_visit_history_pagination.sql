CREATE INDEX IF NOT EXISTS idx_place_visits_couple_place_date_id
    ON place_visits (couple_id, place_id, visited_on DESC, id DESC);
