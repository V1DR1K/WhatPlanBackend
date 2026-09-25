CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_why_fun_venues_couple_updated_created_id
    ON why_fun_venues (couple_id, updated_at DESC, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_why_fun_venues_couple_name_id
    ON why_fun_venues (couple_id, lower(name), id);
