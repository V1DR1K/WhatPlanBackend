-- Venue ratings now belong to a place review. Keep every place and item; only remove the obsolete item-level field.
alter table items drop column if exists venue;
