ALTER TABLE journey_points
    DROP CONSTRAINT ck_journey_points_category,
    ALTER COLUMN category TYPE VARCHAR(50),
    ADD COLUMN extra_actions JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE journey_point_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id', true), '')::uuid
        REFERENCES couples(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(80) NOT NULL,
    icon VARCHAR(20) NOT NULL,
    color VARCHAR(7) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (couple_id, code),
    CHECK (code ~ '^[A-Z][A-Z0-9_-]{0,49}$'),
    CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX idx_journey_point_types_couple_position
    ON journey_point_types(couple_id, position, code);

INSERT INTO journey_point_types (couple_id, code, name, icon, color, position, built_in)
SELECT couple.id, defaults.code, defaults.name, defaults.icon, defaults.color, defaults.position, TRUE
FROM couples couple
CROSS JOIN (VALUES
    ('GENERAL', 'Actividad', 'ACTIVITY', '#B9DCE9', 0),
    ('FOOD', 'WhereFood', 'FOOD', '#FF8A00', 1),
    ('FILM', 'WhichMovie', 'FILM', '#B8ADFF', 2),
    ('COOK', 'WhoCook', 'COOK', '#D4EF55', 3),
    ('FUN', 'WhyFun', 'FUN', '#FFD166', 4),
    ('TRANSFER', 'Traslado', 'TRANSFER', '#83D8F5', 5)
) AS defaults(code, name, icon, color, position);

ALTER TABLE journey_point_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE journey_point_types FORCE ROW LEVEL SECURITY;
CREATE POLICY policy_journey_point_types_couple ON journey_point_types
    USING (couple_id = nullif(current_setting('app.couple_id', true), '')::uuid)
    WITH CHECK (couple_id = nullif(current_setting('app.couple_id', true), '')::uuid);
