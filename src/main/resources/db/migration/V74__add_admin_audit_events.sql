create table admin_audit_events (
  id bigserial primary key,
  actor_user_id bigint not null references users(id),
  actor_username varchar(80) not null,
  couple_id uuid references couples(id),
  action varchar(40) not null,
  http_method varchar(10) not null,
  request_path varchar(300) not null,
  response_status smallint not null,
  occurred_at timestamptz not null default now()
);

create index idx_admin_audit_events_occurred_at on admin_audit_events(occurred_at desc, id desc);
create index idx_admin_audit_events_couple_occurred on admin_audit_events(couple_id, occurred_at desc, id desc);
create index idx_admin_audit_events_actor_occurred on admin_audit_events(actor_user_id, occurred_at desc, id desc);
