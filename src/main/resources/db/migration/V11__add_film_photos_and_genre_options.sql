create table film_photos (
 id bigserial primary key,
 film_id bigint not null unique references films(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 created_at timestamptz not null default now()
);

create table film_genre_options (
 id bigserial primary key,
 name varchar(80) not null unique,
 emoji varchar(20) not null,
 created_at timestamptz not null default now()
);

insert into film_genre_options(name, emoji) values
 ('Acción', '💥'), ('Animación', '🖍️'), ('Aventura', '🗺️'), ('Ciencia ficción', '🚀'),
 ('Comedia', '😂'), ('Crimen', '🕵️'), ('Documental', '🎥'), ('Drama', '🎭'),
 ('Fantasía', '🪄'), ('Misterio', '🧩'), ('Romance', '💘'), ('Suspenso', '😱'),
 ('Terror', '👻');
