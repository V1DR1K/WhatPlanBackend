create table special_dates (
 id bigserial primary key,
 special_date date not null,
 label varchar(160) not null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);

create index idx_special_dates_date_label on special_dates(special_date asc, label asc, id asc);
