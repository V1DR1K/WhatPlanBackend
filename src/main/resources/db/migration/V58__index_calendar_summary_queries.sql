CREATE INDEX IF NOT EXISTS idx_place_visits_couple_date_id
  ON place_visits(couple_id, visited_on DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_film_views_couple_date_id
  ON film_views(couple_id, watched_on DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_why_fun_visits_couple_date_id
  ON why_fun_visits(couple_id, scheduled_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_occurrences_couple_occurred_date_special
  ON special_date_occurrences(couple_id, occurred_on DESC, special_date_id);
