create table global_settings (
 id smallint primary key default 1,
 catalog_page_size integer not null default 5,
 constraint chk_global_settings_singleton check (id = 1),
 constraint chk_global_settings_catalog_page_size check (catalog_page_size between 1 and 50)
);

insert into global_settings (id, catalog_page_size) values (1, 5) on conflict (id) do nothing;
