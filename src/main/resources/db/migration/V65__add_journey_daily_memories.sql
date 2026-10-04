ALTER TABLE journeys
    ADD COLUMN cover_file_id UUID,
    ADD COLUMN max_trip_photos INTEGER NOT NULL DEFAULT 20 CHECK (max_trip_photos BETWEEN 1 AND 100),
    ADD COLUMN max_day_photos INTEGER NOT NULL DEFAULT 10 CHECK (max_day_photos BETWEEN 1 AND 100);

CREATE TABLE journey_days (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id', true), '')::uuid,
    journey_id UUID NOT NULL,
    day DATE NOT NULL,
    story VARCHAR(4000),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, couple_id),
    UNIQUE (journey_id, day, couple_id),
    FOREIGN KEY (journey_id, couple_id) REFERENCES journeys(id, couple_id) ON DELETE CASCADE
);

CREATE TABLE journey_day_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id UUID NOT NULL DEFAULT nullif(current_setting('app.couple_id', true), '')::uuid,
    journey_id UUID NOT NULL,
    day DATE NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    member_id BIGINT NOT NULL,
    rating SMALLINT CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, couple_id),
    FOREIGN KEY (journey_id, day, couple_id) REFERENCES journey_days(journey_id, day, couple_id) ON DELETE CASCADE,
    FOREIGN KEY (member_id, couple_id, user_id) REFERENCES couple_members(id, couple_id, user_id),
    CHECK (rating IS NOT NULL OR length(trim(coalesce(comment, ''))) > 0)
);

CREATE UNIQUE INDEX uq_journey_day_review ON journey_day_reviews(journey_id, day, user_id);
CREATE INDEX idx_journey_days_couple_journey ON journey_days(couple_id, journey_id, day);
CREATE INDEX idx_journey_day_reviews_couple_journey ON journey_day_reviews(couple_id, journey_id, day);

ALTER TABLE journey_files
    ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'ATTACHMENT',
    ADD COLUMN day DATE,
    ADD COLUMN thumbnail_content BYTEA,
    ADD COLUMN width INTEGER,
    ADD COLUMN height INTEGER,
    ADD CONSTRAINT chk_journey_file_purpose CHECK (
        (purpose = 'ATTACHMENT' AND day IS NULL)
        OR (purpose = 'TRIP' AND day IS NULL AND content_type LIKE 'image/%')
        OR (purpose = 'DAY' AND day IS NOT NULL AND content_type LIKE 'image/%')
    ),
    ADD CONSTRAINT fk_journey_day_photo FOREIGN KEY (journey_id, day, couple_id)
        REFERENCES journey_days(journey_id, day, couple_id);

ALTER TABLE journeys
    ADD CONSTRAINT fk_journey_cover_file FOREIGN KEY (cover_file_id, id, couple_id)
        REFERENCES journey_files(id, journey_id, couple_id);

DO $$ DECLARE t TEXT; BEGIN
    FOREACH t IN ARRAY ARRAY['journey_days', 'journey_day_reviews'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING(couple_id=nullif(current_setting(''app.couple_id'',true),'''')::uuid) WITH CHECK(couple_id=nullif(current_setting(''app.couple_id'',true),'''')::uuid)',
            'policy_' || t || '_couple', t);
    END LOOP;
END $$;
