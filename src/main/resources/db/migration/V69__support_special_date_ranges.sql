ALTER TABLE special_dates
    ADD COLUMN ends_on DATE;

UPDATE special_dates
SET ends_on = special_date
WHERE ends_on IS NULL;

ALTER TABLE special_dates
    ALTER COLUMN ends_on SET NOT NULL,
    ADD CONSTRAINT chk_special_dates_range CHECK (ends_on >= special_date);

ALTER TABLE special_date_occurrences
    ADD COLUMN ends_on DATE;

UPDATE special_date_occurrences
SET ends_on = occurred_on
WHERE ends_on IS NULL;

ALTER TABLE special_date_occurrences
    ALTER COLUMN ends_on SET NOT NULL,
    ADD CONSTRAINT chk_special_date_occurrences_range CHECK (ends_on >= occurred_on);
