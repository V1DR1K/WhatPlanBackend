CREATE INDEX IF NOT EXISTS idx_why_fun_venues_couple_scheduled_id
    ON why_fun_venues (couple_id, scheduled_at, id DESC);

CREATE INDEX IF NOT EXISTS idx_why_fun_venues_couple_created_id
    ON why_fun_venues (couple_id, created_at DESC, id DESC);
