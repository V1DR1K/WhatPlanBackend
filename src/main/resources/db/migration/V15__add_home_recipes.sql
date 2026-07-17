create table home_recipes (
 id bigserial primary key,
 author_id bigint not null references users(id),
 home varchar(10) not null check(home in ('TOMAS', 'AVRIL')),
 name varchar(160) not null,
 recipe_url varchar(1000),
 prepared_on date not null,
 meal_type varchar(12) not null check(meal_type in ('DESAYUNO', 'ALMUERZO', 'MERIENDA', 'CENA')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create table home_recipe_ingredients (
 id bigserial primary key,
 recipe_id bigint not null references home_recipes(id) on delete cascade,
 name varchar(160) not null,
 grams integer not null check(grams >= 0),
 position integer not null
);
create table home_recipe_photos (
 id bigserial primary key,
 recipe_id bigint not null unique references home_recipes(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 created_at timestamptz not null default now()
);
create index idx_home_recipes_home_date on home_recipes(home, prepared_on desc, id desc);
