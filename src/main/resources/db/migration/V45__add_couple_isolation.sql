-- Shared-schema tenancy. The legacy records are assigned to the original Tomás/Avril couple.
create table couples (
  id uuid primary key,
  status varchar(16) not null default 'PENDING',
  created_by bigint not null references users(id),
  created_at timestamptz not null default now(),
  closed_at timestamptz,
  version bigint not null default 0,
  constraint chk_couples_status check (status in ('PENDING', 'ACTIVE', 'CLOSED'))
);

create table couple_members (
  id bigserial primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  user_id bigint not null references users(id),
  display_name varchar(100) not null,
  slot smallint not null check (slot between 1 and 2),
  status varchar(16) not null default 'ACTIVE',
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  version bigint not null default 0,
  unique (couple_id, user_id),
  unique (couple_id, slot),
  constraint chk_couple_member_status check (status in ('ACTIVE', 'LEFT'))
);

create unique index uq_couple_member_active_user on couple_members(user_id) where status = 'ACTIVE';
create index idx_couple_members_couple_status on couple_members(couple_id, status);

create table couple_invitations (
  id bigserial primary key,
  couple_id uuid not null references couples(id) on delete cascade,
  created_by bigint not null references users(id),
  token_hash varchar(64) not null unique,
  status varchar(16) not null default 'PENDING',
  expires_at timestamptz not null,
  accepted_by bigint references users(id),
  accepted_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  version bigint not null default 0,
  constraint chk_couple_invitation_status check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
);
create index idx_couple_invitations_couple_status on couple_invitations(couple_id, status);

-- Keep the historical deployment intact while making all existing records tenant-owned.
insert into couples(id, status, created_by, created_at)
select '00000000-0000-0000-0000-000000000001'::uuid, 'ACTIVE', u.id, now()
from users u
where lower(u.username) = 'tomas'
  and not exists (select 1 from couples where id = '00000000-0000-0000-0000-000000000001'::uuid);

insert into couple_members(couple_id, user_id, display_name, slot, status)
select '00000000-0000-0000-0000-000000000001'::uuid, u.id,
       case when lower(u.username) = 'tomas' then 'Tomás' else 'Avril' end,
       case when lower(u.username) = 'tomas' then 1 else 2 end,
       'ACTIVE'
from users u
where lower(u.username) in ('tomas', 'avril')
  and not exists (select 1 from couple_members m where m.couple_id = '00000000-0000-0000-0000-000000000001'::uuid and m.user_id = u.id);

update couples set status = 'ACTIVE'
where id = '00000000-0000-0000-0000-000000000001'::uuid
  and (select count(*) from couple_members m where m.couple_id = couples.id and m.status = 'ACTIVE') = 2;

do $$
declare
  table_name text;
  tenant_tables text[] := array[
    'places', 'place_visits', 'items', 'item_photos', 'item_reviews', 'place_photos', 'place_reviews',
    'place_visit_photos', 'place_visit_reviews', 'place_highlight_tags',
    'films', 'film_photos', 'film_reviews', 'film_views', 'film_genres',
    'recipes', 'recipe_ingredients', 'recipe_steps', 'recipe_photos', 'cookings', 'cooking_reviews',
    'home_recipes', 'home_recipe_ingredients', 'home_recipe_steps', 'home_recipe_photos', 'home_recipe_reviews',
    'why_fun_venues', 'why_fun_venue_schedules', 'why_fun_venue_photos', 'why_fun_venue_reviews',
    'why_fun_visits', 'why_fun_visit_photos', 'why_fun_visit_reviews',
    'special_dates', 'special_date_occurrences', 'special_date_occurrence_comments', 'special_date_occurrence_photos'
  ];
begin
  foreach table_name in array tenant_tables loop
    execute format('alter table %I add column couple_id uuid', table_name);
    execute format('update %I set couple_id = %L::uuid where couple_id is null', table_name, '00000000-0000-0000-0000-000000000001');
    execute format('alter table %I alter column couple_id set default nullif(current_setting(''app.couple_id'', true), '''')::uuid', table_name);
    execute format('alter table %I alter column couple_id set not null', table_name);
    execute format('alter table %I add constraint %I foreign key (couple_id) references couples(id)', table_name, 'fk_' || table_name || '_couple');
    execute format('create index %I on %I(couple_id)', 'idx_' || table_name || '_couple', table_name);
    execute format('alter table %I enable row level security', table_name);
    execute format('alter table %I force row level security', table_name);
    execute format('create policy %I on %I using (couple_id = nullif(current_setting(''app.couple_id'', true), '''')::uuid) with check (couple_id = nullif(current_setting(''app.couple_id'', true), '''')::uuid)', 'policy_' || table_name || '_couple', table_name);
  end loop;
end $$;
