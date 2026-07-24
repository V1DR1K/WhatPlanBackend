alter table cooking_reviews
  add column complexity smallint not null default 1 check (complexity between 1 and 5);
