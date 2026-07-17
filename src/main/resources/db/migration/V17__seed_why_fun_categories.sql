insert into why_fun_categories(name, slug, icon)
select preset.name, preset.slug, preset.icon
from (values
  ('Juegos y desafíos', 'juegos-y-desafios', '🎮'),
  ('Aire libre', 'aire-libre', '🌳'),
  ('Deporte', 'deporte', '⚽'),
  ('Cultura y shows', 'cultura-y-shows', '🎭'),
  ('Paseos y experiencias', 'paseos-y-experiencias', '✨')
) as preset(name, slug, icon)
where not exists (
  select 1 from why_fun_categories category where category.parent_id is null and category.slug = preset.slug
);

with presets(parent_slug, name, slug, icon) as (
  values
    ('juegos-y-desafios', 'Videojuegos y arcade', 'videojuegos-y-arcade', '🕹️'),
    ('juegos-y-desafios', 'Bowling', 'bowling', '🎳'),
    ('juegos-y-desafios', 'Pool y billar', 'pool-y-billar', '🎱'),
    ('juegos-y-desafios', 'Juegos de mesa', 'juegos-de-mesa', '🎲'),
    ('juegos-y-desafios', 'Escape room', 'escape-room', '🔐'),
    ('juegos-y-desafios', 'Karaoke', 'karaoke', '🎤'),
    ('aire-libre', 'Parque', 'parque', '🏞️'),
    ('aire-libre', 'Caminata', 'caminata', '🥾'),
    ('aire-libre', 'Picnic', 'picnic', '🧺'),
    ('aire-libre', 'Bicicleta y patines', 'bicicleta-y-patines', '🚲'),
    ('aire-libre', 'Senderismo', 'senderismo', '⛰️'),
    ('aire-libre', 'Plaza', 'plaza', '🌿'),
    ('deporte', 'Pádel', 'padel', '🎾'),
    ('deporte', 'Fútbol', 'futbol', '⚽'),
    ('deporte', 'Escalada', 'escalada', '🧗'),
    ('deporte', 'Patinaje', 'patinaje', '⛸️'),
    ('deporte', 'Natación', 'natacion', '🏊'),
    ('deporte', 'Tenis', 'tenis', '🏸'),
    ('cultura-y-shows', 'Cine', 'cine', '🎬'),
    ('cultura-y-shows', 'Teatro', 'teatro', '🎭'),
    ('cultura-y-shows', 'Recital', 'recital', '🎸'),
    ('cultura-y-shows', 'Museo', 'museo', '🖼️'),
    ('cultura-y-shows', 'Exposición', 'exposicion', '🪄'),
    ('cultura-y-shows', 'Taller', 'taller', '🧑‍🎨'),
    ('paseos-y-experiencias', 'Feria', 'feria', '🛍️'),
    ('paseos-y-experiencias', 'Tour', 'tour', '🗺️'),
    ('paseos-y-experiencias', 'Escapada', 'escapada', '🚗'),
    ('paseos-y-experiencias', 'Mirador', 'mirador', '🌅'),
    ('paseos-y-experiencias', 'Parque de diversiones', 'parque-de-diversiones', '🎢'),
    ('paseos-y-experiencias', 'Aventura', 'aventura', '🧭')
)
insert into why_fun_categories(parent_id, name, slug, icon)
select parent.id, preset.name, preset.slug, preset.icon
from presets preset
join why_fun_categories parent on parent.parent_id is null and parent.slug = preset.parent_slug
where not exists (
  select 1 from why_fun_categories category where category.parent_id = parent.id and category.slug = preset.slug
);
