create table film_views (
 id bigserial primary key,
 film_id bigint not null references films(id) on delete cascade,
 created_by bigint not null references users(id),
 watched_on date not null,
 created_at timestamptz not null default now(),
 unique(film_id, watched_on)
);

insert into film_views(film_id, created_by, watched_on, created_at)
select review.film_id, min(review.author_id), coalesce(review.watched_on, film.last_watched_on, film.created_at::date), min(review.created_at)
from film_reviews review
join films film on film.id = review.film_id
group by review.film_id, coalesce(review.watched_on, film.last_watched_on, film.created_at::date);

insert into film_views(film_id, created_by, watched_on, created_at)
select film.id, film.created_by, coalesce(film.last_watched_on, film.created_at::date), film.created_at
from films film
where film.watched_count > 0
  and not exists (select 1 from film_views film_view where film_view.film_id = film.id);

alter table film_reviews add column view_id bigint references film_views(id) on delete cascade;

update film_reviews review
set view_id = film_view.id
from film_views film_view
join films film on film.id = film_view.film_id
where review.film_id = film_view.film_id
  and film_view.watched_on = coalesce(review.watched_on, film.last_watched_on, film.created_at::date);

alter table film_reviews alter column view_id set not null;
alter table film_reviews drop column watched_on;
alter table film_reviews add constraint film_reviews_view_id_author_id_key unique(view_id, author_id);

create index idx_film_views_film_watched_on on film_views(film_id, watched_on desc, id desc);
create index idx_film_reviews_view_id on film_reviews(view_id);

update films film
set watched_count = summary.count,
    last_watched_on = summary.last_watched_on
from (
 select film_id, count(*)::integer as count, max(watched_on) as last_watched_on
 from film_views
 group by film_id
) summary
where summary.film_id = film.id;

update films
set watched_count = 0,
    last_watched_on = null
where not exists (select 1 from film_views film_view where film_view.film_id = films.id);
