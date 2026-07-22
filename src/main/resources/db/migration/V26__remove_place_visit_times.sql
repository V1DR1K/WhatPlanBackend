with ranked_visits as (
  select id,
    first_value(id) over (partition by place_id, visited_on order by id) as keep_id,
    row_number() over (partition by place_id, visited_on order by id) as position
  from place_visits
)
update items item
set visit_id = ranked.keep_id
from ranked_visits ranked
where item.visit_id = ranked.id and ranked.position > 1;

delete from place_visits visit
using (
  select id
  from (
    select id, row_number() over (partition by place_id, visited_on order by id) as position
    from place_visits
  ) ranked
  where position > 1
) duplicate
where visit.id = duplicate.id;

alter table place_visits drop column visited_at;
drop index if exists uq_place_visits_legacy_day;
drop index if exists uq_place_visits_day_time;
create unique index uq_place_visits_day on place_visits(place_id, visited_on);
drop index if exists idx_place_visits_place_when;
create index idx_place_visits_place_day on place_visits(place_id, visited_on desc, id desc);
