CREATE INDEX IF NOT EXISTS idx_why_fun_visits_couple_venue_keyset
  ON why_fun_visits (couple_id, venue_id, scheduled_at DESC NULLS LAST, id DESC);
