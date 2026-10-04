ALTER TABLE journey_packing_items
    ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_journey_packing_order
    ON journey_packing_items (journey_id, couple_id, user_id, position, id);
