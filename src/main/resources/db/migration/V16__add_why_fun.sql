create table why_fun_categories (
 id bigserial primary key,
 parent_id bigint references why_fun_categories(id),
 name varchar(80) not null,
 slug varchar(80) not null,
 icon varchar(20) not null,
 active boolean not null default true,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create unique index uq_why_fun_root_category_slug on why_fun_categories(slug) where parent_id is null;
create unique index uq_why_fun_subcategory_slug on why_fun_categories(parent_id, slug) where parent_id is not null;

create table why_fun_venues (
 id bigserial primary key,
 name varchar(160) not null,
 address varchar(250) not null,
 category_id bigint not null references why_fun_categories(id),
 subcategory_id bigint not null references why_fun_categories(id),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index idx_why_fun_venues_category_id_desc on why_fun_venues(category_id, id desc);
create index idx_why_fun_venues_subcategory_id_desc on why_fun_venues(subcategory_id, id desc);

create table why_fun_venue_schedules (
 id bigserial primary key,
 venue_id bigint not null references why_fun_venues(id) on delete cascade,
 day_of_week varchar(12) not null,
 opens_at time not null,
 closes_at time not null,
 check (opens_at <> closes_at),
 unique (venue_id, day_of_week, opens_at)
);
create index idx_why_fun_schedules_venue_day on why_fun_venue_schedules(venue_id, day_of_week, opens_at);

create table why_fun_venue_photos (
 id bigserial primary key,
 venue_id bigint not null references why_fun_venues(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 created_at timestamptz not null default now()
);
create index idx_why_fun_photos_venue_id on why_fun_venue_photos(venue_id, id);

create table why_fun_venue_reviews (
 id bigserial primary key,
 venue_id bigint not null references why_fun_venues(id) on delete cascade,
 author_id bigint not null references users(id),
 rating smallint not null check (rating between 1 and 5),
 comment varchar(1000),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique (venue_id, author_id)
);
create index idx_why_fun_reviews_venue_id on why_fun_venue_reviews(venue_id);
