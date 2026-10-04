package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import com.wherefood.config.CoupleContext;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** SQL identifiers come only from this closed enum, never from request values. */
@Repository
public class JourneySourceRepository {
    public enum Section {
        FOOD("places", "place_visits", "place_id", "visited_on", "name", "/app/food/places/"),
        FILM("films", "film_views", "film_id", "watched_on", "title", "/app/films/"),
        COOK("recipes", "cookings", "recipe_id", "cooked_on", "name", "/app/how-cook/"),
        FUN(
                "why_fun_venues",
                "why_fun_visits",
                "venue_id",
                "scheduled_at",
                "name",
                "/app/why-fun/");
        final String catalog, experiences, parent, date, title, href;

        Section(
                String catalog,
                String experiences,
                String parent,
                String date,
                String title,
                String href) {
            this.catalog = catalog;
            this.experiences = experiences;
            this.parent = parent;
            this.date = date;
            this.title = title;
            this.href = href;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public JourneySourceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Section section(String name) {
        try {
            return Section.valueOf(name);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sección inválida");
        }
    }

    public SourceDto source(String name, Long id) {
        Section s = section(name);
        var rows =
                jdbc.query(
                        "select id,"
                                + s.title
                                + " as title,zone_id from "
                                + s.catalog
                                + " where id=:id and couple_id=:couple",
                        Map.of("id", id, "couple", CoupleContext.current()),
                        (rs, n) ->
                                new SourceDto(
                                        name,
                                        rs.getLong("id"),
                                        rs.getString("title"),
                                        rs.getLong("zone_id"),
                                        s.href + rs.getLong("id")));
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha no encontrada");
        return rows.getFirst();
    }

    public List<SourceDto> sources(String name, Long cityId, String search) {
        Section s = section(name);
        Map<String, Object> params = new HashMap<>();
        params.put("couple", CoupleContext.current());
        params.put("city", cityId);
        params.put("search", search == null ? "" : search.toLowerCase(Locale.ROOT));
        String location = s == Section.FOOD || s == Section.FUN ? "zone_id=:city" : "true";
        String active = s == Section.FOOD ? " and deactivated_at is null" : "";
        return jdbc.query(
                "select id,"
                        + s.title
                        + " as title,zone_id from "
                        + s.catalog
                        + " c where couple_id=:couple"
                        + active
                        + " and (cast(:city as bigint) is null or "
                        + location
                        + ") and position(:search in lower("
                        + s.title
                        + "))>0 order by "
                        + s.title
                        + ",id limit 100",
                params,
                (rs, n) ->
                        new SourceDto(
                                name,
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("zone_id"),
                                s.href + rs.getLong("id")));
    }

    public List<JourneyDayEntryDto> journeyDayEntries(UUID journeyId, java.time.LocalDate day) {
        List<JourneyDayEntryDto> result = new ArrayList<>();
        for (Section section : Section.values()) {
            String linkedColumn = switch (section) {
                case FOOD -> "place_visit_id";
                case FILM -> "film_view_id";
                case COOK -> "cooking_id";
                case FUN -> "fun_visit_id";
            };
            String detail = switch (section) {
                case FOOD, FUN -> "coalesce(c.address, '')";
                case FILM -> "'Película vista'";
                case COOK -> "'Preparación en casa'";
            };
            Map<String, Object> params = Map.of(
                    "journey", journeyId, "day", day, "couple", CoupleContext.current());
            String sql = "select distinct e.id, c." + section.title + " as title, " + detail
                    + " as detail, s.id as stage_id from " + section.experiences + " e join "
                    + section.catalog + " c on c.id=e." + section.parent
                    + " and c.couple_id=e.couple_id join journey_stages s on s.journey_id=:journey"
                    + " and s.couple_id=:couple and :day between s.starts_on and s.ends_on"
                    + " and (e.stage_id=s.id or e.stage_id is null) where e.couple_id=:couple"
                    + " and e." + section.date + "=:day"
                    + ((section == Section.FOOD || section == Section.FUN)
                            ? " and e.city_id=s.city_id" : "")
                    + " and not exists(select 1 from journey_points p where p." + linkedColumn
                    + "=e.id and p.couple_id=e.couple_id and p.journey_id<>:journey)"
                    + " and (not exists(select 1 from journey_points p where p." + linkedColumn
                    + "=e.id and p.couple_id=e.couple_id) or exists(select 1 from journey_points p"
                    + " where p." + linkedColumn + "=e.id and p.couple_id=e.couple_id"
                    + " and p.journey_id=:journey and p.stage_id=s.id))"
                    + " order by c." + section.title + ",e.id";
            List<ExperienceRow> rows = jdbc.query(sql, params, (rs, n) -> new ExperienceRow(
                    rs.getLong("id"), rs.getString("title"), rs.getString("detail")));
            for (ExperienceRow row : rows) {
                List<JourneySourcePhotoDto> photos = daySourcePhotos(section, row.id());
                result.add(new JourneyDayEntryDto(
                        section.name() + ":" + row.id(), section.name(), day, row.title(),
                        row.detail(), section.href + row.id(), photos));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(JourneyDayEntryDto::section)
                        .thenComparing(JourneyDayEntryDto::title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(JourneyDayEntryDto::id))
                .toList();
    }

    private record ExperienceRow(Long id, String title, String detail) {}

    private List<JourneySourcePhotoDto> daySourcePhotos(Section section, Long experienceId) {
        UUID couple = CoupleContext.current();
        String sql;
        if (section == Section.FOOD) {
            sql = "select 'FOOD:'||p.id as id, '/place-visit-photos/'||p.id as url, "
                    + "'/place-visit-photos/'||p.id||'?thumbnail=true' as thumb, p.width,p.height "
                    + "from place_visit_photos p where p.visit_id=:id and p.couple_id=:couple "
                    + "union all select 'FOOD:PLACE:'||p.id, '/places/'||p.place_id||'/photo?v='||p.id, "
                    + "'/places/'||p.place_id||'/photo?thumbnail=true&v='||p.id,p.width,p.height "
                    + "from place_photos p join place_visits v on v.place_id=p.place_id "
                    + "and v.couple_id=p.couple_id where v.id=:id and v.cover_photo_id is null "
                    + "and v.couple_id=:couple and not exists(select 1 from place_visit_photos x "
                    + "where x.visit_id=v.id and x.couple_id=v.couple_id)";
        } else if (section == Section.FILM) {
            sql = "select 'FILM:'||p.id, '/films/'||p.film_id||'/photo?v='||p.id, "
                    + "'/films/'||p.film_id||'/photo?thumbnail=true&v='||p.id,p.width,p.height "
                    + "from film_photos p join film_views v on v.film_id=p.film_id "
                    + "and v.couple_id=p.couple_id where v.id=:id and v.couple_id=:couple";
        } else if (section == Section.COOK) {
            sql = "select 'COOK:'||p.id, '/how-cook/recipes/'||p.recipe_id||'/photo?v='||p.id, "
                    + "'/how-cook/recipes/'||p.recipe_id||'/photo?thumbnail=true&v='||p.id,p.width,p.height "
                    + "from recipe_photos p join cookings v on v.recipe_id=p.recipe_id "
                    + "and v.couple_id=p.couple_id where v.id=:id and v.couple_id=:couple";
        } else {
            sql = "select 'FUN:VISIT:'||p.id, '/why-fun/activity-visit-photos/'||p.id, "
                    + "'/why-fun/activity-visit-photos/'||p.id||'?thumbnail=true',p.width,p.height "
                    + "from why_fun_visit_photos p where p.visit_id=:id and p.couple_id=:couple "
                    + "union all select 'FUN:VENUE:'||p.id, '/why-fun/photos/'||p.id, "
                    + "'/why-fun/photos/'||p.id||'?thumbnail=true',p.width,p.height "
                    + "from why_fun_venue_photos p join why_fun_visits v on v.venue_id=p.venue_id "
                    + "and v.couple_id=p.couple_id where v.id=:id and v.couple_id=:couple "
                    + "and not exists(select 1 from why_fun_visit_photos x where x.visit_id=v.id "
                    + "and x.couple_id=v.couple_id)";
        }
        return jdbc.query(sql, Map.of("id", experienceId, "couple", couple), (rs, n) ->
                new JourneySourcePhotoDto(rs.getString("id"), rs.getString("url"),
                        rs.getString("thumb"), rs.getInt("width"), rs.getInt("height")));
    }

    public List<ExperienceDto> experiences(
            String name,
            Long entityId,
            java.time.LocalDate from,
            java.time.LocalDate to,
            int page) {
        Section s = section(name);
        source(name, entityId);
        if (page < 0 || page > 10000 || from != null && to != null && to.isBefore(from))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rango de experiencias inválido");
        Map<String, Object> params = new HashMap<>();
        params.put("parent", entityId);
        params.put("couple", CoupleContext.current());
        params.put("offset", page * 100);
        params.put("from", from);
        params.put("to", to);
        return jdbc.query(
                "select id,"
                        + s.date
                        + " as date,city_id,stage_id from "
                        + s.experiences
                        + " where "
                        + s.parent
                        + "=:parent and couple_id=:couple and (cast(:from as date) is null or "
                        + s.date
                        + ">=:from) and (cast(:to as date) is null or "
                        + s.date
                        + "<=:to) order by "
                        + s.date
                        + " desc,id desc limit 100 offset :offset",
                params,
                (rs, n) ->
                        new ExperienceDto(
                                rs.getLong("id"),
                                rs.getObject("date", java.time.LocalDate.class),
                                rs.getLong("city_id"),
                                rs.getObject("stage_id", UUID.class)));
    }

    public ExperienceDto experience(String name, Long entityId, Long id) {
        Section s = section(name);
        var rows =
                jdbc.query(
                        "select id,"
                                + s.date
                                + " as date,city_id,stage_id from "
                                + s.experiences
                                + " where id=:id and "
                                + s.parent
                                + "=:parent and couple_id=:couple",
                        Map.of("id", id, "parent", entityId, "couple", CoupleContext.current()),
                        (rs, n) ->
                                new ExperienceDto(
                                        rs.getLong("id"),
                                        rs.getObject("date", java.time.LocalDate.class),
                                        rs.getLong("city_id"),
                                        rs.getObject("stage_id", UUID.class)));
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Experiencia no encontrada");
        return rows.getFirst();
    }

    public void locate(String name, Long id, Long cityId, UUID stageId) {
        Section s = section(name);
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("couple", CoupleContext.current());
        params.put("city", cityId);
        params.put("stage", stageId);
        if (jdbc.update(
                        "update "
                                + s.experiences
                                + " set city_id=:city,stage_id=:stage,version=version+1 where"
                                + " id=:id and couple_id=:couple",
                        params)
                != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Experiencia no encontrada");
    }

    public Long memberId(Long userId) {
        var rows =
                jdbc.queryForList(
                        "select id from couple_members where couple_id=:couple and user_id=:user"
                            + " and status='ACTIVE'",
                        Map.of("couple", CoupleContext.current(), "user", userId),
                        Long.class);
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Elegí un integrante de la pareja");
        return rows.getFirst();
    }

    public List<MemberDto> members() {
        return jdbc.query(
                "select u.id,u.username from couple_members m join users u on u.id=m.user_id where"
                    + " m.couple_id=:couple and m.status='ACTIVE' order by m.slot",
                Map.of("couple", CoupleContext.current()),
                (rs, n) -> new MemberDto(rs.getLong(1), rs.getString(2)));
    }

    public boolean invalidPhysicalCity(String name, Long entity, Long city) {
        Section s = section(name);
        if (s != Section.FOOD && s != Section.FUN) return false;
        String point = s == Section.FOOD ? "place_id" : "venue_id";
        return jdbc.queryForObject(
                "select exists(select 1 from "
                        + s.experiences
                        + " where "
                        + s.parent
                        + "=:entity and couple_id=:couple and city_id<>:city union all select 1"
                        + " from journey_points p join journey_stages st on st.id=p.stage_id and"
                        + " st.couple_id=p.couple_id where p."
                        + point
                        + "=:entity and p.couple_id=:couple and st.city_id<>:city)",
                Map.of("entity", entity, "city", city, "couple", CoupleContext.current()),
                Boolean.class);
    }

    public List<LinkedDateDto> dates(UUID journeyId) {
        return jdbc.query(
                "select o.special_date_id,o.occurred_on,d.label,o.stage_id from"
                    + " special_date_occurrences o join special_dates d on d.id=o.special_date_id"
                    + " and d.couple_id=o.couple_id join journey_stages s on s.id=o.stage_id and"
                    + " s.couple_id=o.couple_id where s.journey_id=:journey and o.couple_id=:couple"
                    + " order by o.occurred_on",
                Map.of("journey", journeyId, "couple", CoupleContext.current()),
                (rs, n) ->
                        new LinkedDateDto(
                                rs.getLong(1),
                                rs.getObject(2, java.time.LocalDate.class),
                                rs.getString(3),
                                rs.getObject(4, UUID.class)));
    }

    public boolean stageHasExperiences(UUID stage) {
        return jdbc.queryForObject(
                "select exists(select 1 from place_visits where stage_id=:id union all select 1"
                    + " from film_views where stage_id=:id union all select 1 from cookings where"
                    + " stage_id=:id union all select 1 from why_fun_visits where stage_id=:id"
                    + " union all select 1 from special_date_occurrences where stage_id=:id)",
                Map.of("id", stage),
                Boolean.class);
    }

    public boolean outside(UUID stage, java.time.LocalDate from, java.time.LocalDate to) {
        Map<String, Object> params = Map.of("id", stage, "from", from, "to", to);
        StringBuilder q = new StringBuilder("select exists(");
        for (Section s : Section.values()) {
            if (s != Section.FOOD) q.append(" union all ");
            q.append("select 1 from ")
                    .append(s.experiences)
                    .append(" where stage_id=:id and (")
                    .append(s.date)
                    .append("<:from or ")
                    .append(s.date)
                    .append(">:to)");
        }
        q.append(
                " union all select 1 from special_date_occurrences where stage_id=:id and"
                    + " (occurred_on<:from or occurred_on>:to))");
        return jdbc.queryForObject(q.toString(), params, Boolean.class);
    }
}
