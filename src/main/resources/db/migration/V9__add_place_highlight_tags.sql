create table highlight_tags (id bigserial primary key, name varchar(60) not null unique, emoji varchar(20) not null);
create table place_highlight_tags (place_id bigint not null references places(id) on delete cascade, tag_id bigint not null references highlight_tags(id) on delete cascade, primary key(place_id, tag_id));
insert into highlight_tags(name, emoji) values
  ('Medialunas', '🥐'), ('Tortas', '🍰'), ('Café de especialidad', '☕'), ('Brunch', '🍳'),
  ('Hamburguesas', '🍔'), ('Papas fritas', '🍟'), ('Pizza', '🍕'), ('Empanadas', '🥟'),
  ('Helado', '🍦'), ('Pastas', '🍝'), ('Cervezas', '🍺'), ('Tragos', '🍸'), ('Vino', '🍷'),
  ('Vegano', '🌱'), ('Sin TACC', '🌾'), ('Postres', '🍮');
update users set role = 'ADMIN' where username in ('avril', 'tomas');
