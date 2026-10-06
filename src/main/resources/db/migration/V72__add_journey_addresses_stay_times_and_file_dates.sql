ALTER TABLE journey_points
    ADD COLUMN address VARCHAR(500);

ALTER TABLE journey_stays
    ADD COLUMN check_in_time TIME,
    ADD COLUMN check_out_time TIME;

ALTER TABLE journey_files
    ADD COLUMN occurred_at TIMESTAMPTZ;

-- These tables use FORCE RLS. Temporarily disable it for a migration-owner backfill.
ALTER TABLE journey_files NO FORCE ROW LEVEL SECURITY;

UPDATE journey_files
SET occurred_at = created_at,
    name = CASE
        WHEN content_type LIKE 'image/%' THEN 'Foto'
        ELSE 'Documento'
    END;

ALTER TABLE journey_files
    ALTER COLUMN occurred_at SET NOT NULL;

ALTER TABLE journey_files FORCE ROW LEVEL SECURITY;
