-- Supports the couple-scoped archived-place cursor query without indexing active rows.
CREATE INDEX idx_places_archived_couple_deactivated_id
    ON places (couple_id, deactivated_at DESC, id DESC)
    WHERE deactivated_at IS NOT NULL;
