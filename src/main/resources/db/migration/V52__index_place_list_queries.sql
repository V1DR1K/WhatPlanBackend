CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_couple_updated_created_active
    ON places (couple_id, updated_at DESC, created_at DESC, id DESC)
    WHERE deactivated_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_place_highlight_tags_couple_tag_place
    ON place_highlight_tags (couple_id, tag_id, place_id);
