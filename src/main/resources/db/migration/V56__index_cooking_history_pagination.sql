CREATE INDEX IF NOT EXISTS idx_cookings_couple_recipe_date_id
    ON cookings (couple_id, recipe_id, cooked_on DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cookings_couple_date_id
    ON cookings (couple_id, cooked_on DESC, id DESC);
