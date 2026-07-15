create table place_reviews (
 id bigserial primary key,
 place_id bigint not null references places(id) on delete cascade,
 author_id bigint not null references users(id),
 comment varchar(1000),
 location smallint not null check(location between 1 and 5),
 heating smallint not null check(heating between 1 and 5),
 bathrooms smallint not null check(bathrooms between 1 and 5),
 exterior smallint not null check(exterior between 1 and 5),
 seating smallint not null check(seating between 1 and 5),
 service smallint not null check(service between 1 and 5),
 ambiance smallint not null check(ambiance between 1 and 5),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(place_id, author_id)
);
create index idx_place_reviews_place_id on place_reviews(place_id);
create table place_photos (
 id bigserial primary key,
 place_id bigint not null unique references places(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 created_at timestamptz not null default now()
);
