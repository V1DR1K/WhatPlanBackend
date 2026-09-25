CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_films_couple_updated_created_id
    ON films (couple_id, updated_at DESC, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_films_couple_platform_updated
    ON films (couple_id, platform_id, updated_at DESC, created_at DESC, id DESC);
