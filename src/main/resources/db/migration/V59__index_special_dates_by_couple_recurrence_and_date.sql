CREATE INDEX IF NOT EXISTS idx_special_dates_couple_recurrence_date
  ON special_dates(couple_id, recurrence, special_date);
