CREATE INDEX idx_special_dates_once_range
    ON special_dates USING GIST (daterange(special_date, ends_on, '[]'))
    WHERE recurrence = 'ONCE';
