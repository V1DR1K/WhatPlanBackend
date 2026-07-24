alter table special_dates add column recurrence varchar(16);

update special_dates set recurrence = 'ONCE';

alter table special_dates alter column recurrence set not null;

alter table special_dates add constraint chk_special_dates_recurrence
  check (recurrence in ('ONCE', 'ANNUAL', 'MONTHLY'));

insert into special_dates (special_date, label, recurrence)
select defaults.special_date, defaults.label, defaults.recurrence
from (values
  (date '2026-02-14', 'San Valentín', 'ANNUAL'),
  (date '2026-10-03', 'Día del Novio', 'ANNUAL'),
  (date '2026-06-27', 'Nuestro aniversario', 'ANNUAL'),
  (date '2026-06-27', 'Mensuario', 'MONTHLY'),
  (date '2026-05-03', 'Primera vez que hablamos', 'ANNUAL'),
  (date '2026-05-09', 'Primera cita', 'ANNUAL'),
  (date '2005-04-12', 'Cumpleaños de Tomás', 'ANNUAL'),
  (date '2004-04-12', 'Cumpleaños de Avril', 'ANNUAL')
) as defaults(special_date, label, recurrence)
where not exists (
  select 1
  from special_dates existing
  where existing.special_date = defaults.special_date
    and existing.label = defaults.label
    and existing.recurrence = defaults.recurrence
);
