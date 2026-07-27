create table special_date_occurrences (
 id bigserial primary key,
 special_date_id bigint not null references special_dates(id) on delete cascade,
 occurred_on date not null,
 cover_photo_id bigint,
 created_by bigint not null references users(id),
 updated_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique (special_date_id, occurred_on)
);

create table special_date_occurrence_comments (
 id bigserial primary key,
 occurrence_id bigint not null references special_date_occurrences(id) on delete cascade,
 author_id bigint not null references users(id),
 updated_by bigint not null references users(id),
 comment varchar(2000) not null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique (occurrence_id, author_id)
);

create table special_date_occurrence_photos (
 id bigserial primary key,
 occurrence_id bigint not null references special_date_occurrences(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 position integer not null check (position >= 0),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 unique (occurrence_id, position)
);

alter table special_date_occurrences add constraint fk_special_date_occurrence_cover foreign key (cover_photo_id) references special_date_occurrence_photos(id) on delete set null;
create index idx_special_date_occurrence_comments on special_date_occurrence_comments(occurrence_id, author_id);
create index idx_special_date_occurrence_photos on special_date_occurrence_photos(occurrence_id, position);
