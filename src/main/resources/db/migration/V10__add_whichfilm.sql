create table watch_platforms (
 id bigserial primary key,
 name varchar(80) not null unique,
 icon varchar(20) not null,
 active boolean not null default true,
 created_at timestamptz not null default now()
);

insert into watch_platforms(name, icon) values
 ('Netflix', '🍿'), ('Prime Video', '📦'), ('Disney+', '✨'), ('Max', '🎬'),
 ('Paramount+', '🏔️'), ('MUBI', '🎞️'), ('Cine', '🎟️'), ('Otro', '📺');

create table films (
 id bigserial primary key,
 tmdb_id bigint unique,
 title varchar(200) not null,
 original_title varchar(200),
 synopsis varchar(3000),
 release_date date,
 poster_path varchar(300),
 platform_id bigint references watch_platforms(id),
 watched_count integer not null default 0 check(watched_count >= 0),
 last_watched_on date,
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);

create table film_genres (
 film_id bigint not null references films(id) on delete cascade,
 name varchar(80) not null,
 primary key(film_id, name)
);

create table film_reviews (
 id bigserial primary key,
 film_id bigint not null references films(id) on delete cascade,
 author_id bigint not null references users(id),
 rating smallint not null check(rating between 1 and 5),
 comment varchar(1000),
 watched_on date,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(film_id, author_id)
);

create index idx_films_platform_id on films(platform_id);
create index idx_films_updated_at on films(updated_at desc);
create index idx_film_reviews_film_id on film_reviews(film_id);
