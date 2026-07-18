alter table places add column deactivated_at timestamptz;
create index idx_places_active_status_id on places(status, id desc) where deactivated_at is null;
