ALTER TABLE why_fun_venues
    ADD COLUMN single_occurrence boolean NOT NULL DEFAULT false,
    ADD COLUMN start_date date,
    ADD COLUMN end_date date,
    ADD CONSTRAINT chk_why_fun_venue_occurrence_dates CHECK (
        (single_occurrence = false AND start_date IS NULL AND end_date IS NULL)
        OR
        (single_occurrence = true AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date)
    );
