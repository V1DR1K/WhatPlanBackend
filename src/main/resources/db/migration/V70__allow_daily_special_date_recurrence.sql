ALTER TABLE special_dates
    DROP CONSTRAINT chk_special_dates_recurrence;

ALTER TABLE special_dates
    ADD CONSTRAINT chk_special_dates_recurrence
        CHECK (recurrence IN ('ONCE', 'ANNUAL', 'MONTHLY', 'DAILY'));
