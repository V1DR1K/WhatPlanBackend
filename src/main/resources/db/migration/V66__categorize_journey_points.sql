ALTER TABLE journey_points
    ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'GENERAL';

UPDATE journey_points
SET category = CASE
    WHEN place_id IS NOT NULL THEN 'FOOD'
    WHEN film_id IS NOT NULL THEN 'FILM'
    WHEN recipe_id IS NOT NULL THEN 'COOK'
    WHEN venue_id IS NOT NULL THEN 'FUN'
    ELSE 'GENERAL'
END;

ALTER TABLE journey_points
    ADD CONSTRAINT ck_journey_points_category
        CHECK (category IN ('GENERAL', 'FOOD', 'FILM', 'COOK', 'FUN', 'TRANSFER'));
