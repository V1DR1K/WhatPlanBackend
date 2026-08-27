alter table place_reviews add column version bigint not null default 0;
alter table place_visit_reviews add column version bigint not null default 0;
alter table item_reviews add column version bigint not null default 0;
alter table film_reviews add column version bigint not null default 0;
alter table cooking_reviews add column version bigint not null default 0;
alter table why_fun_venue_reviews add column version bigint not null default 0;
alter table why_fun_visit_reviews add column version bigint not null default 0;
alter table special_date_occurrence_comments add column version bigint not null default 0;
