CREATE INDEX IF NOT EXISTS idx_recipes_couple_updated_created_id
    ON recipes (couple_id, updated_at DESC, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cookings_couple_recipe_home
    ON cookings (couple_id, recipe_id, home);
