alter table cooking_reviews
  add column taste smallint not null default 1 check (taste between 1 and 5);

update cooking_reviews
set taste = rating;
