package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import com.wherefood.domain.*;
import com.wherefood.repo.JourneyRepositories.*;
import com.wherefood.web.PhotoStorage;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class JourneyService {
    private final Journeys trips;
    private final Stages stages;
    private final Points points;
    private final Stays stays;
    private final PackingItems packing;
    private final Movements movements;
    private final Files files;
    private final Reviews reviews;
    private final DayReviews journeyDayReviews;
    private final com.wherefood.repo.Repositories.SpecialDates dateTemplates;
    private final com.wherefood.repo.Repositories.SpecialDateOccurrences dateOccurrences;
    private final LocationService locations;
    private final JourneySourceRepository sources;
    private final JourneyPointTypeService pointTypes;
    private final EntityManager em;
    private final PhotoStorage photoStorage;
    private final long maxFileBytes;

    public JourneyService(
            Journeys trips,
            Stages stages,
            Points points,
            Stays stays,
            PackingItems packing,
            Movements movements,
            Files files,
            Reviews reviews,
            DayReviews journeyDayReviews,
            LocationService locations,
            JourneySourceRepository sources,
            JourneyPointTypeService pointTypes,
            EntityManager em,
            PhotoStorage photoStorage,
            com.wherefood.repo.Repositories.SpecialDates dateTemplates,
            com.wherefood.repo.Repositories.SpecialDateOccurrences dateOccurrences,
            @Value("${app.journey.max-upload-bytes:10485760}") long maxFileBytes) {
        this.trips = trips;
        this.stages = stages;
        this.points = points;
        this.stays = stays;
        this.packing = packing;
        this.movements = movements;
        this.files = files;
        this.reviews = reviews;
        this.journeyDayReviews = journeyDayReviews;
        this.dateTemplates = dateTemplates;
        this.dateOccurrences = dateOccurrences;
        this.locations = locations;
        this.sources = sources;
        this.pointTypes = pointTypes;
        this.em = em;
        this.photoStorage = photoStorage;
        this.maxFileBytes = maxFileBytes;
    }

    private UUID couple() {
        return LocationService.couple();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private <T extends JourneyEntity> T owned(Scoped<T> repo, UUID id) {
        return repo.findByIdAndCoupleId(id, couple())
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Registro no encontrado"));
    }

    private Journey trip(UUID id, boolean edit) {
        Journey trip = owned(trips, id);
        if (edit && trip.archived) throw conflict("El viaje está archivado");
        return trip;
    }

    private JourneyStage stage(UUID tripId, UUID id) {
        JourneyStage value = owned(stages, id);
        if (!value.journeyId.equals(tripId)) throw bad("La etapa no pertenece a este viaje");
        return value;
    }

    private void dates(LocalDate start, LocalDate end, LocalDate outerStart, LocalDate outerEnd) {
        if (start == null
                || end == null
                || end.isBefore(start)
                || start.isBefore(outerStart)
                || end.isAfter(outerEnd))
            throw bad("Las fechas deben estar dentro del período indicado");
    }

    public List<TripDto> list(int page, int size) {
        return list(page, size, null, null, null, null, null, null, null);
    }

    public List<TripDto> list(
            int page,
            int size,
            Boolean archived,
            String search,
            String status,
            Long destinationId,
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw bad("La fecha inicial no puede ser posterior a la fecha final");
        if (destinationId != null && destinationId <= 0)
            throw bad("Elegí un destino válido");

        String normalizedSearch = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        if (normalizedSearch != null && normalizedSearch.isEmpty()) normalizedSearch = null;
        String normalizedStatus = normalizeTripStatus(status);
        Sort ordering = tripSort(sortBy);
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        return trips
                .findFiltered(
                        couple(),
                        archived,
                        normalizedSearch,
                        destinationId,
                        fromDate,
                        toDate,
                        normalizedStatus,
                        today,
                        PageRequest.of(
                                Math.max(0, page),
                                Math.clamp(size, 1, 50),
                                ordering))
                .stream()
                .map(this::dto)
                .toList();
    }

    private String normalizeTripStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return switch (status.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "UPCOMING", "IN_PROGRESS", "FINISHED" ->
                    status.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            default -> throw bad("Elegí un estado de viaje válido");
        };
    }

    private Sort tripSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank() || sortBy.equalsIgnoreCase("starts-desc"))
            return Sort.by(Sort.Order.desc("startsOn"), Sort.Order.desc("id"));
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "starts-asc" -> Sort.by(Sort.Order.asc("startsOn"), Sort.Order.asc("id"));
            case "name-asc" -> Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
            default -> throw bad("Elegí un orden de viajes válido");
        };
    }

    public List<CityDto> destinations() {
        List<Long> cityIds = stages.findByCoupleId(couple()).stream()
                .filter(stage -> stage.position == 0)
                .map(stage -> stage.cityId)
                .distinct()
                .toList();
        return locations.citiesByIds(cityIds);
    }

    private TripDto dto(Journey t) {
        UUID coverId = t.coverFileId;
        if (coverId == null) {
            coverId = files.findByJourneyIdAndCoupleIdAndPurposeOrderByCreatedAtAscIdAsc(
                    t.id, couple(), "TRIP").stream().findFirst().map(f -> f.id).orElseGet(() ->
                    files.findByJourneyIdAndCoupleIdAndPurposeOrderByCreatedAtAscIdAsc(
                            t.id, couple(), "DAY").stream().findFirst().map(f -> f.id).orElse(null));
        }
        return new TripDto(
                t.id,
                t.name,
                t.startsOn,
                t.endsOn,
                t.archived,
                stages.findByJourneyIdAndCoupleId(t.id, couple()).stream()
                        .sorted(Comparator.comparingInt(s -> s.position))
                        .limit(1)
                        .map(
                                s -> {
                                    CityDto c = locations.city(s.cityId);
                                    return new StageDto(
                                            s.id,
                                            c.id(),
                                            c.name(),
                                            c.countryCode(),
                                            s.startsOn,
                                            s.endsOn,
                                            s.position);
                                })
                        .toList(),
                coverId,
                coverId == null ? null : "/whither-journey/files/" + coverId + "/content?thumbnail=true",
                t.maxTripPhotos <= 0 ? 20 : t.maxTripPhotos,
                t.maxDayPhotos <= 0 ? 10 : t.maxDayPhotos);
    }

    @Transactional
    public TripDto saveTrip(UUID id, TripRequest request) {
        if (request.endsOn().isBefore(request.startsOn()))
            throw bad("La fecha de fin debe ser igual o posterior al inicio");
        if (request.stages().size() != 1)
            throw bad("Cada viaje debe tener un solo destino");
        StageRequest requestedStage = request.stages().getFirst();
        if (!requestedStage.startsOn().equals(request.startsOn())
                || !requestedStage.endsOn().equals(request.endsOn()))
            throw bad("El destino debe cubrir todo el período del viaje");
        Journey t = id == null ? new Journey() : trip(id, true);
        if (id != null && (files.existsByJourneyIdAndCoupleIdAndPurposeAndDayBefore(
                        id, couple(), "DAY", request.startsOn())
                || files.existsByJourneyIdAndCoupleIdAndPurposeAndDayAfter(
                        id, couple(), "DAY", request.endsOn())
                || journeyDayReviews.existsByJourneyIdAndCoupleIdAndDayBefore(
                        id, couple(), request.startsOn())
                || journeyDayReviews.existsByJourneyIdAndCoupleIdAndDayAfter(
                        id, couple(), request.endsOn())))
            throw conflict("Hay fotos o reseñas diarias fuera del nuevo período");
        t.name = request.name().trim();
        t.startsOn = request.startsOn();
        t.endsOn = request.endsOn();
        t.maxTripPhotos = request.maxTripPhotos() == null
                ? (id == null ? 20 : t.maxTripPhotos) : request.maxTripPhotos();
        t.maxDayPhotos = request.maxDayPhotos() == null
                ? (id == null ? 10 : t.maxDayPhotos) : request.maxDayPhotos();
        if (id == null) t.createdAt = Instant.now();
        trips.saveAndFlush(t);
        List<JourneyStage> existing = stages.findByJourneyIdAndCoupleId(t.id, couple());
        Set<UUID> retained = new HashSet<>();
        int position = 0;
        for (StageRequest r : request.stages()) {
            dates(r.startsOn(), r.endsOn(), t.startsOn, t.endsOn);
            locations.city(r.cityId());
            JourneyStage s = r.id() == null ? new JourneyStage() : stage(t.id, r.id());
            if (r.id() != null && !retained.add(r.id()))
                throw bad("Una etapa no puede repetirse en el formulario");
            if (r.id() != null) validateStageChange(s, r);
            s.journeyId = t.id;
            s.cityId = r.cityId();
            s.startsOn = r.startsOn();
            s.endsOn = r.endsOn();
            s.position = position++;
            stages.save(s);
        }
        for (JourneyStage old : existing)
            if (!retained.contains(old.id)) {
                // Keep contentful legacy stages intact so a single-destination edit does not
                // detach old experiences. They remain hidden from the current trip interface.
                if (!hasStageContent(t.id, old.id)) stages.delete(old);
            }
        em.flush();
        return dto(t);
    }

    private boolean hasStageContent(UUID tripId, UUID stageId) {
        return sources.stageHasExperiences(stageId)
                || points.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                        .anyMatch(p -> stageId.equals(p.stageId))
                || stays.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                        .anyMatch(s -> stageId.equals(s.stageId))
                || movements.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                        .anyMatch(m -> stageId.equals(m.stageId))
                || files.findSummariesByJourneyIdAndCoupleId(tripId, couple()).stream()
                        .anyMatch(f -> stageId.equals(f.getStageId()));
    }

    private void validateStageChange(JourneyStage s, StageRequest r) {
        if (!s.cityId.equals(r.cityId()) && hasStageContent(s.journeyId, s.id))
            throw conflict("Reubicá el contenido antes de cambiar la ciudad");
        boolean out =
                points.findByJourneyIdAndCoupleId(s.journeyId, couple()).stream()
                                .anyMatch(
                                        p ->
                                                s.id.equals(p.stageId)
                                                        && p.scheduledOn != null
                                                        && (p.scheduledOn.isBefore(r.startsOn())
                                                                || p.scheduledOn.isAfter(
                                                                        r.endsOn())))
                        || stays.findByJourneyIdAndCoupleId(s.journeyId, couple()).stream()
                                .anyMatch(
                                        v ->
                                                s.id.equals(v.stageId)
                                                        && (v.startsOn.isBefore(r.startsOn())
                                                                || v.endsOn.isAfter(r.endsOn())))
                        || sources.outside(s.id, r.startsOn(), r.endsOn());
        if (out) throw conflict("Hay experiencias, puntos o estadías fuera de las nuevas fechas");
    }

    @Transactional
    public void archive(UUID id) {
        Journey t = trip(id, true);
        t.archived = true;
        trips.save(t);
    }

    @Transactional
    public void deleteTrip(UUID id) {
        trip(id, false);
        if (points.existsByJourneyIdAndCoupleId(id, couple())
                || stays.existsByJourneyIdAndCoupleId(id, couple())
                || packing.existsByJourneyIdAndCoupleId(id, couple())
                || movements.existsByJourneyIdAndCoupleId(id, couple())
                || files.existsByJourneyIdAndCoupleId(id, couple())
                || reviews.existsByJourneyIdAndCoupleId(id, couple())
                || journeyDayReviews.existsByJourneyIdAndCoupleId(id, couple())
                || stages.findByJourneyIdAndCoupleId(id, couple()).stream()
                        .anyMatch(s -> sources.stageHasExperiences(s.id)))
            throw conflict("Solo podés borrar viajes sin contenido; podés archivar este viaje");
        stages.deleteAll(stages.findByJourneyIdAndCoupleId(id, couple()));
        em.flush();
        trips.delete(owned(trips, id));
    }

    public DetailDto detail(UUID id) {
        Journey t = trip(id, false);
        var ms = movements.findByJourneyIdAndCoupleId(id, couple());
        return new DetailDto(
                dto(t),
                points.findByJourneyIdAndCoupleId(id, couple()).stream()
                        .sorted(
                                Comparator.comparing(
                                                (JourneyPoint p) -> p.scheduledOn,
                                                Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparingInt(p -> p.position))
                        .map(this::pointDto)
                        .toList(),
                stays.findByJourneyIdAndCoupleId(id, couple()).stream().map(this::stayDto).toList(),
                packing.findByJourneyIdAndCoupleId(id, couple()).stream()
                        .sorted(
                                Comparator.comparing((JourneyPackingItem p) -> p.userId)
                                        .thenComparingInt(p -> p.position)
                                        .thenComparing(p -> p.id))
                        .map(
                                v ->
                                        new PackingDto(
                                                v.id,
                                                v.userId,
                                                v.description,
                                                v.quantity,
                                                v.packed,
                                                v.position))
                        .toList(),
                ms.stream().map(this::movementDto).toList(),
                balances(ms),
                reviews.findByJourneyIdAndCoupleId(id, couple()).stream()
                        .map(this::reviewDto)
                        .toList(),
                files.findSummariesByJourneyIdAndCoupleId(id, couple()).stream()
                        .map(this::fileDto)
                        .toList(),
                sources.members(),
                sources.dates(id),
                stageBalances(ms));
    }

    private List<StageBalanceDto> stageBalances(List<JourneyMovement> ms) {
        Map<UUID, List<JourneyMovement>> grouped = new HashMap<>();
        for (JourneyMovement m : ms)
            grouped.computeIfAbsent(m.stageId, k -> new ArrayList<>()).add(m);
        return grouped.entrySet().stream()
                .map(e -> new StageBalanceDto(e.getKey(), balances(e.getValue())))
                .toList();
    }

    public static List<BalanceDto> balances(List<JourneyMovement> values) {
        Map<String, BigDecimal[]> totals = new TreeMap<>();
        for (JourneyMovement m : values) {
            BigDecimal[] t =
                    totals.computeIfAbsent(
                            m.currency,
                            k ->
                                    new BigDecimal[] {
                                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                                    });
            int index =
                    switch (m.kind) {
                        case "FUNDS" -> 0;
                        case "EXPENSE" -> 1;
                        case "REFUND" -> 2;
                        default -> throw new IllegalArgumentException("Movimiento inválido");
                    };
            t[index] = t[index].add(m.amount);
        }
        return totals.entrySet().stream()
                .map(
                        e ->
                                new BalanceDto(
                                        e.getKey(),
                                        e.getValue()[0],
                                        e.getValue()[1],
                                        e.getValue()[2],
                                        e.getValue()[0]
                                                .subtract(e.getValue()[1])
                                                .add(e.getValue()[2])))
                .toList();
    }

    private SourceRef ref(JourneyPoint p) {
        if (p.placeId != null) return new SourceRef("FOOD", p.placeId, p.placeVisitId);
        if (p.filmId != null) return new SourceRef("FILM", p.filmId, p.filmViewId);
        if (p.recipeId != null) return new SourceRef("COOK", p.recipeId, p.cookingId);
        if (p.venueId != null) return new SourceRef("FUN", p.venueId, p.funVisitId);
        return null;
    }

    private PointDto pointDto(JourneyPoint p) {
        return new PointDto(
                p.id,
                p.stageId,
                p.title,
                p.scheduledOn,
                p.scheduledTime,
                p.notes,
                p.mapsUrl,
                p.position,
                p.status,
                ref(p),
                p.category,
                p.extraActions == null ? List.of() : p.extraActions.stream()
                        .map(action -> new PointActionDto(action.label(), action.icon(), action.url()))
                        .toList(),
                p.address);
    }

    @Transactional
    public PointDto savePoint(UUID tripId, UUID id, PointRequest r) {
        trip(tripId, true);
        JourneyStage s = stage(tripId, r.stageId());
        if (r.scheduledOn() != null) dates(r.scheduledOn(), r.scheduledOn(), s.startsOn, s.endsOn);
        JourneyPoint p = id == null ? new JourneyPoint() : owned(points, id);
        if (id != null && !p.journeyId.equals(tripId)) throw bad("Punto de otro viaje");
        if (id != null
                && !p.stageId.equals(r.stageId())
                && (movements.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                                .anyMatch(m -> id.equals(m.pointId))
                        || files.findSummariesByJourneyIdAndCoupleId(tripId, couple()).stream()
                                .anyMatch(f -> id.equals(f.getPointId()))))
            throw conflict(
                    "Reubicá los movimientos y archivos antes de cambiar la etapa del punto");
        SourceRef old = ref(p);
        if (old != null
                && old.experienceId() != null
                && !p.stageId.equals(r.stageId()))
            throw conflict("Reubicá la experiencia desde su sección antes de cambiar el punto");
        if (old != null
                && old.experienceId() != null
                && !Objects.equals(old, r.source()))
            detachExperienceFromJourney(old);
        p.journeyId = tripId;
        p.stageId = s.id;
        p.title = r.title().trim();
        p.scheduledOn = r.scheduledOn();
        p.scheduledTime = r.scheduledTime();
        p.notes = r.notes();
        p.mapsUrl = r.mapsUrl();
        p.address = r.address();
        p.position = r.position();
        p.status = r.status();
        p.category = pointTypes.requireCategory(
                r.category() != null ? r.category()
                        : r.source() != null ? r.source().section() : "GENERAL");
        if (r.extraActions() != null) {
            p.extraActions = r.extraActions().stream()
                    .map(action -> new JourneyPointAction(action.label().trim(), action.icon(), action.url().trim()))
                    .toList();
        } else if (p.extraActions == null) {
            p.extraActions = new ArrayList<>();
        }
        p.placeId =
                p.filmId =
                        p.recipeId =
                                p.venueId =
                                        p.placeVisitId =
                                                p.filmViewId = p.cookingId = p.funVisitId = null;
        if (r.source() != null) {
            SourceDto source = sources.source(r.source().section(), r.source().entityId());
            if ((source.section().equals("FOOD") || source.section().equals("FUN"))
                    && !source.cityId().equals(s.cityId)) throw bad("El lugar está en otra ciudad");
            setSource(p, r.source());
        }
        if (r.status().equals("COMPLETED")
                && r.source() != null
                && r.source().experienceId() == null)
            throw bad("Registrá o elegí la experiencia para completar este punto");
        if (r.source() != null && r.source().experienceId() != null) {
            ExperienceDto exp =
                    sources.experience(
                            r.source().section(), r.source().entityId(), r.source().experienceId());
            dates(exp.date(), exp.date(), s.startsOn, s.endsOn);
            if (exp.stageId() != null && !exp.stageId().equals(s.id))
                throw conflict("La experiencia ya pertenece a otra etapa");
            ensureUnused(r.source(), p.id);
            sources.locate(r.source().section(), exp.id(), s.cityId, s.id);
            p.scheduledOn = exp.date();
        }
        points.saveAndFlush(p);
        return pointDto(p);
    }

    @Transactional
    public void reorder(UUID tripId, List<UUID> ids) {
        trip(tripId, true);
        if (ids == null || ids.size() > 1000 || new HashSet<>(ids).size() != ids.size())
            throw bad("Orden inválido");
        int position = 0;
        for (UUID id : ids) {
            JourneyPoint p = owned(points, id);
            if (!p.journeyId.equals(tripId)) throw bad("Punto de otro viaje");
            p.position = position++;
            points.save(p);
        }
    }

    private void setSource(JourneyPoint p, SourceRef r) {
        switch (r.section()) {
            case "FOOD" -> {
                p.placeId = r.entityId();
                p.placeVisitId = r.experienceId();
            }
            case "FILM" -> {
                p.filmId = r.entityId();
                p.filmViewId = r.experienceId();
            }
            case "COOK" -> {
                p.recipeId = r.entityId();
                p.cookingId = r.experienceId();
            }
            case "FUN" -> {
                p.venueId = r.entityId();
                p.funVisitId = r.experienceId();
            }
            default -> throw bad("Sección inválida");
        }
    }

    private void detachExperienceFromJourney(SourceRef source) {
        ExperienceDto experience =
                sources.experience(source.section(), source.entityId(), source.experienceId());
        sources.locate(source.section(), experience.id(), experience.cityId(), null);
    }

    private void ensureUnused(SourceRef source, UUID pointId) {
        if (source.experienceId() == null) return;
        Long count =
                em.createQuery(
                                "select count(p) from JourneyPoint p where p.coupleId=:couple and"
                                    + " p."
                                        + switch (source.section()) {
                                            case "FOOD" -> "placeVisitId";
                                            case "FILM" -> "filmViewId";
                                            case "COOK" -> "cookingId";
                                            case "FUN" -> "funVisitId";
                                            default -> throw bad("Sección inválida");
                                        }
                                        + "=:id and (:point is null or p.id<>:point)",
                                Long.class)
                        .setParameter("couple", couple())
                        .setParameter("id", source.experienceId())
                        .setParameter("point", pointId)
                        .getSingleResult();
        if (count > 0) throw conflict("La experiencia ya está vinculada a otro punto");
    }

    @Transactional
    public void deletePoint(UUID id) {
        JourneyPoint p = owned(points, id);
        trip(p.journeyId, true);
        SourceRef source = ref(p);
        if (source != null && source.experienceId() != null)
            detachExperienceFromJourney(source);
        for (JourneyMovement movement :
                movements.findByJourneyIdAndCoupleIdAndPointId(p.journeyId, couple(), id)) {
            movement.pointId = null;
            if (movement.stageId == null) movement.stageId = p.stageId;
            movements.save(movement);
        }
        for (JourneyFile file : files.findByJourneyIdAndCoupleIdAndPointId(p.journeyId, couple(), id)) {
            file.pointId = null;
            if (file.stageId == null) file.stageId = p.stageId;
            files.save(file);
        }
        points.delete(p);
    }

    private StayDto stayDto(JourneyStay s) {
        return new StayDto(
                s.id,
                s.stageId,
                s.name,
                s.startsOn,
                s.endsOn,
                s.address,
                s.price,
                s.currency,
                s.source,
                s.bookingUrl,
                s.mapsUrl,
                s.photoId,
                s.checkInTime,
                s.checkOutTime);
    }

    @Transactional
    public StayDto saveStay(UUID tripId, UUID id, StayRequest r) {
        trip(tripId, true);
        JourneyStage st = stage(tripId, r.stageId());
        dates(r.startsOn(), r.endsOn(), st.startsOn, st.endsOn);
        if (r.price() != null && r.currency() == null)
            throw bad("Indicá la moneda del alojamiento");
        if (r.currency() != null) currency(r.currency());
        JourneyStay s = id == null ? new JourneyStay() : owned(stays, id);
        if (id != null && !s.journeyId.equals(tripId)) throw bad("Estadía de otro viaje");
        if (id != null
                && !s.stageId.equals(st.id)
                && (movements.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                                .anyMatch(m -> id.equals(m.stayId))
                        || files.findSummariesByJourneyIdAndCoupleId(tripId, couple()).stream()
                                .anyMatch(f -> id.equals(f.getStayId()))))
            throw conflict(
                    "Reubicá los movimientos y archivos antes de cambiar la etapa del alojamiento");
        s.journeyId = tripId;
        s.stageId = st.id;
        s.name = r.name().trim();
        s.startsOn = r.startsOn();
        s.endsOn = r.endsOn();
        s.checkInTime = r.checkInTime();
        s.checkOutTime = r.checkOutTime();
        s.address = r.address();
        s.price = r.price();
        s.currency = r.currency();
        s.source = r.source();
        s.bookingUrl = r.bookingUrl();
        s.mapsUrl = r.mapsUrl();
        if (r.photoId() != null) {
            JourneyFile cover = owned(files, r.photoId());
            if (!tripId.equals(cover.journeyId)
                    || !Objects.equals(id, cover.stayId)
                    || cover.contentType == null
                    || !cover.contentType.startsWith("image/"))
                throw bad("La portada debe ser una foto de este alojamiento");
        }
        s.photoId = r.photoId();
        stays.save(s);
        return stayDto(s);
    }

    @Transactional
    public void deleteStay(UUID id) {
        JourneyStay s = owned(stays, id);
        trip(s.journeyId, true);
        if (movements.findByJourneyIdAndCoupleId(s.journeyId, couple()).stream()
                        .anyMatch(m -> id.equals(m.stayId))
                || files.findSummariesByJourneyIdAndCoupleId(s.journeyId, couple()).stream()
                        .anyMatch(f -> id.equals(f.getStayId()))
                || reviews.findByJourneyIdAndCoupleId(s.journeyId, couple()).stream()
                        .anyMatch(r -> id.equals(r.stayId)))
            throw conflict("Reubicá los archivos, reseñas y gastos antes de borrar la estadía");
        stays.delete(s);
    }

    @Transactional
    public PackingDto savePacking(UUID tripId, UUID id, PackingRequest r) {
        trip(tripId, true);
        if (sources.members().stream().noneMatch(m -> m.id().equals(r.userId())))
            throw bad("Elegí un integrante de la pareja");
        JourneyPackingItem p = id == null ? new JourneyPackingItem() : owned(packing, id);
        if (id != null && !p.journeyId.equals(tripId)) throw bad("Valija de otro viaje");
        Long previousUserId = p.userId;
        boolean wasPacked = p.packed;
        p.journeyId = tripId;
        p.userId = r.userId();
        p.memberId = sources.memberId(r.userId());
        p.description = r.description().trim();
        p.quantity = r.quantity();
        p.packed = r.packed();
        if (id == null || !Objects.equals(previousUserId, p.userId) || (!wasPacked && p.packed))
            p.position = nextPackingPosition(tripId, p.userId);
        packing.save(p);
        return packingDto(p);
    }

    @Transactional
    public List<PackingDto> savePackingForBoth(UUID tripId, PackingBothRequest request) {
        trip(tripId, true);
        List<MemberDto> members = sources.members();
        if (members.size() != 2) throw bad("Se necesitan ambos integrantes de la pareja");
        return members.stream()
                .map(
                        member ->
                                savePacking(
                                        tripId,
                                        null,
                                        new PackingRequest(
                                                member.id(),
                                                request.description(),
                                                request.quantity(),
                                                false)))
                .toList();
    }

    @Transactional
    public void reorderPacking(UUID tripId, PackingOrderRequest request) {
        trip(tripId, true);
        List<JourneyPackingItem> items =
                packing.findByJourneyIdAndCoupleIdAndUserIdOrderByPositionAscIdAsc(
                        tripId, couple(), request.userId());
        List<UUID> existingIds = items.stream().map(item -> item.id).toList();
        if (existingIds.size() != request.itemIds().size()
                || new HashSet<>(request.itemIds()).size() != request.itemIds().size()
                || !new HashSet<>(existingIds).equals(new HashSet<>(request.itemIds())))
            throw bad("El orden debe incluir todos los elementos de esta valija");

        Map<UUID, JourneyPackingItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.id, item));
        for (int index = 0; index < request.itemIds().size(); index++)
            byId.get(request.itemIds().get(index)).position = index;
        packing.saveAll(items);
    }

    @Transactional
    public void deletePacking(UUID id) {
        JourneyPackingItem p = owned(packing, id);
        trip(p.journeyId, true);
        packing.delete(p);
    }

    private MovementDto movementDto(JourneyMovement m) {
        return new MovementDto(
                m.id,
                m.stageId,
                m.pointId,
                m.stayId,
                m.kind,
                m.description,
                m.amount,
                m.currency,
                m.occurredOn);
    }

    private int nextPackingPosition(UUID tripId, Long userId) {
        return packing.findByJourneyIdAndCoupleIdAndUserIdOrderByPositionAscIdAsc(
                                tripId, couple(), userId)
                        .stream()
                        .mapToInt(item -> item.position)
                        .max()
                        .orElse(-1)
                + 1;
    }

    private PackingDto packingDto(JourneyPackingItem item) {
        return new PackingDto(
                item.id,
                item.userId,
                item.description,
                item.quantity,
                item.packed,
                item.position);
    }

    private void currency(String code) {
        try {
            Currency.getInstance(code);
        } catch (Exception e) {
            throw bad("Moneda inválida");
        }
    }

    private UUID associationStage(UUID stageId, UUID pointId, UUID stayId, UUID movementId) {
        UUID result = stageId;
        List<UUID> linked = new ArrayList<>();
        if (pointId != null) linked.add(owned(points, pointId).stageId);
        if (stayId != null) linked.add(owned(stays, stayId).stageId);
        if (movementId != null) linked.add(owned(movements, movementId).stageId);
        for (UUID value : linked)
            if (value != null) {
                if (result != null && !result.equals(value))
                    throw bad("Las vinculaciones deben corresponder a la misma etapa");
                result = value;
            }
        return result;
    }

    private void associations(
            UUID tripId, UUID stageId, UUID pointId, UUID stayId, UUID movementId) {
        if (stageId != null) stage(tripId, stageId);
        if (pointId != null) {
            JourneyPoint p = owned(points, pointId);
            if (!p.journeyId.equals(tripId) || (stageId != null && !p.stageId.equals(stageId)))
                throw bad("El punto no corresponde al viaje y etapa");
        }
        if (stayId != null) {
            JourneyStay s = owned(stays, stayId);
            if (!s.journeyId.equals(tripId) || (stageId != null && !s.stageId.equals(stageId)))
                throw bad("La estadía no corresponde al viaje y etapa");
        }
        if (movementId != null && !owned(movements, movementId).journeyId.equals(tripId))
            throw bad("Gasto de otro viaje");
    }

    @Transactional
    public MovementDto saveMovement(UUID tripId, UUID id, MovementRequest r) {
        trip(tripId, true);
        currency(r.currency());
        associations(tripId, r.stageId(), r.pointId(), r.stayId(), null);
        associationStage(r.stageId(), r.pointId(), r.stayId(), null);
        JourneyMovement m = id == null ? new JourneyMovement() : owned(movements, id);
        if (id != null && !m.journeyId.equals(tripId)) throw bad("Movimiento de otro viaje");
        m.journeyId = tripId;
        m.stageId = associationStage(r.stageId(), r.pointId(), r.stayId(), null);
        m.pointId = r.pointId();
        m.stayId = r.stayId();
        m.kind = r.kind();
        m.description = r.description().trim();
        m.amount = r.amount();
        m.currency = r.currency();
        m.occurredOn = r.occurredOn();
        movements.save(m);
        return movementDto(m);
    }

    @Transactional
    public void deleteMovement(UUID id) {
        JourneyMovement m = owned(movements, id);
        trip(m.journeyId, true);
        if (files.findSummariesByJourneyIdAndCoupleId(m.journeyId, couple()).stream()
                .anyMatch(f -> id.equals(f.getMovementId())))
            throw conflict("Reubicá los archivos antes de borrar el movimiento");
        movements.delete(m);
    }

    private ReviewDto reviewDto(JourneyReview r) {
        String author =
                sources.members().stream()
                        .filter(m -> m.id().equals(r.userId))
                        .map(MemberDto::username)
                        .findFirst()
                        .orElse("Integrante anterior");
        return new ReviewDto(r.id, r.stayId, r.userId, author, r.rating, r.comment);
    }

    @Transactional
    public ReviewDto review(UUID tripId, ReviewRequest request, User actor) {
        trip(tripId, true);
        associations(tripId, null, null, request.stayId(), null);
        if (sources.members().stream().noneMatch(m -> m.id().equals(actor.id)))
            throw bad("Necesitás pertenecer a la pareja");
        JourneyReview r =
                reviews.findByJourneyIdAndCoupleId(tripId, couple()).stream()
                        .filter(
                                v ->
                                        v.userId.equals(actor.id)
                                                && Objects.equals(v.stayId, request.stayId()))
                        .findFirst()
                        .orElseGet(JourneyReview::new);
        r.journeyId = tripId;
        r.stayId = request.stayId();
        r.userId = actor.id;
        r.memberId = sources.memberId(actor.id);
        r.rating = request.rating();
        r.comment = request.comment();
        reviews.save(r);
        return reviewDto(r);
    }

    private FileDto fileDto(JourneyFile f) {
        return new FileDto(
                f.id,
                f.name,
                f.contentType,
                f.byteSize,
                f.stageId,
                f.pointId,
                f.stayId,
                f.movementId,
                "/whither-journey/files/" + f.id + "/content",
                f.purpose == null ? "ATTACHMENT" : f.purpose, f.day, f.width, f.height,
                f.thumbnailContent == null ? null
                        : "/whither-journey/files/" + f.id + "/content?thumbnail=true",
                f.occurredAt);
    }

    private FileDto fileDto(FileSummary f) {
        return new FileDto(
                f.getId(),
                f.getName(),
                f.getContentType(),
                f.getByteSize(),
                f.getStageId(),
                f.getPointId(),
                f.getStayId(),
                f.getMovementId(),
                "/whither-journey/files/" + f.getId() + "/content",
                f.getPurpose() == null ? "ATTACHMENT" : f.getPurpose(), f.getDay(),
                f.getWidth(), f.getHeight(),
                "TRIP".equals(f.getPurpose()) || "DAY".equals(f.getPurpose())
                        ? "/whither-journey/files/" + f.getId() + "/content?thumbnail=true"
                        : null,
                f.getOccurredAt());
    }

    public JourneyFile file(UUID id) {
        return owned(files, id);
    }

    public static String fileType(byte[] b) {
        if (b.length >= 5
                && b[0] == '%'
                && b[1] == 'P'
                && b[2] == 'D'
                && b[3] == 'F'
                && b[4] == '-') return "application/pdf";
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "image/jpeg";
        if (b.length >= 8
                && Arrays.equals(
                        Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
            return "image/png";
        if (b.length >= 12
                && new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
            return "image/webp";
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Solo se admiten PDF, JPEG, PNG y WebP");
    }

    @Transactional
    public FileDto upload(
            UUID tripId,
            UUID stageId,
            UUID pointId,
            UUID stayId,
            UUID movementId,
            boolean hotelPhoto,
            MultipartFile upload)
            throws java.io.IOException {
        return upload(tripId, stageId, pointId, stayId, movementId, hotelPhoto, null, upload);
    }

    @Transactional
    public FileDto upload(
            UUID tripId,
            UUID stageId,
            UUID pointId,
            UUID stayId,
            UUID movementId,
            boolean hotelPhoto,
            Instant occurredAt,
            MultipartFile upload)
            throws java.io.IOException {
        trip(tripId, true);
        associations(tripId, stageId, pointId, stayId, movementId);
        associationStage(stageId, pointId, stayId, movementId);
        if (upload.isEmpty() || upload.getSize() > maxFileBytes)
            throw bad("El archivo está vacío o supera el límite permitido");
        byte[] bytes = upload.getBytes();
        String type = fileType(bytes);
        if (hotelPhoto && (stayId == null || !type.startsWith("image/")))
            throw bad("La foto del alojamiento debe ser una imagen");
        if (stayId != null && type.startsWith("image/")) {
            long stayPhotoCount =
                    files.findSummariesByJourneyIdAndCoupleId(tripId, couple()).stream()
                            .filter(file -> stayId.equals(file.getStayId()))
                            .filter(file -> file.getContentType() != null
                                    && file.getContentType().startsWith("image/"))
                            .count();
            if (stayPhotoCount >= 5)
                throw conflict("Cada alojamiento admite hasta 5 fotos");
        }
        JourneyFile f = new JourneyFile();
        f.journeyId = tripId;
        f.stageId = associationStage(stageId, pointId, stayId, movementId);
        f.pointId = pointId;
        f.stayId = stayId;
        f.movementId = movementId;
        f.purpose = "ATTACHMENT";
        f.name = type.startsWith("image/") ? "Foto" : "Documento";
        f.contentType = type;
        f.content = bytes;
        f.byteSize = bytes.length;
        if (type.startsWith("image/")) {
            PhotoStorage.ImageData image = photoStorage.processJourneyPhoto(upload);
            f.thumbnailContent = Base64.getDecoder().decode(image.thumbnail());
            f.width = image.width();
            f.height = image.height();
        }
        f.createdAt = Instant.now();
        f.occurredAt = occurredAt == null ? f.createdAt : occurredAt;
        files.saveAndFlush(f);
        if (hotelPhoto) {
            JourneyStay s = owned(stays, stayId);
            s.photoId = f.id;
            stays.save(s);
        }
        return fileDto(f);
    }

    @Transactional
    public FileDto updateFileDate(UUID id, Instant occurredAt) {
        if (occurredAt == null) throw bad("Indicá la fecha del archivo");
        JourneyFile file = owned(files, id);
        trip(file.journeyId, true);
        file.occurredAt = occurredAt;
        return fileDto(files.save(file));
    }

    @Transactional
    public void deleteFile(UUID id) {
        JourneyFile f = owned(files, id);
        Journey journey = trip(f.journeyId, true);
        if (id.equals(journey.coverFileId)) {
            journey.coverFileId = null;
            trips.saveAndFlush(journey);
        }
        for (JourneyStay s : stays.findByJourneyIdAndCoupleId(f.journeyId, couple()))
            if (id.equals(s.photoId)) {
                s.photoId = null;
                stays.save(s);
            }
        em.flush();
        files.delete(f);
    }

    public List<SourceDto> catalog(String section, Long city, String search) {
        couple();
        return sources.sources(section, city, search);
    }

    public List<ExperienceDto> experiences(
            String section, Long entity, LocalDate from, LocalDate to, int page) {
        couple();
        return sources.experiences(section, entity, from, to, page);
    }

    public ExperienceLocation experienceLocation(String section, Long entity, Long experience) {
        ExperienceDto e = sources.experience(section, entity, experience);
        JourneyPoint p =
                e.stageId() == null
                        ? null
                        : points
                                .findByJourneyIdAndCoupleId(
                                        owned(stages, e.stageId()).journeyId, couple())
                                .stream()
                                .filter(
                                        v -> {
                                            SourceRef r = ref(v);
                                            return r != null
                                                    && r.section().equals(section)
                                                    && experience.equals(r.experienceId());
                                        })
                                .findFirst()
                                .orElse(null);
        return new ExperienceLocation(
                e.cityId(), e.stageId(), p == null ? null : p.id, p == null ? null : p.journeyId);
    }

    @Transactional
    public ExperienceLocation bind(String section, Long entity, Long experience, BindingRequest r) {
        em.flush();
        SourceDto source = sources.source(section, entity);
        ExperienceDto exp = sources.experience(section, entity, experience);
        Long city = r.cityId() == null ? locations.origin() : r.cityId();
        locations.city(city);
        JourneyStage st = r.stageId() == null ? null : owned(stages, r.stageId());
        if (st != null) {
            trip(st.journeyId, true);
            if ((section.equals("FOOD") || section.equals("FUN"))
                    && r.cityId() != null
                    && !r.cityId().equals(st.cityId))
                throw bad("La ciudad no coincide con la etapa");
            city = st.cityId;
            dates(exp.date(), exp.date(), st.startsOn, st.endsOn);
        }
        if ((section.equals("FOOD") || section.equals("FUN")) && !city.equals(source.cityId()))
            throw bad("La experiencia debe estar en la ciudad del lugar");
        if (r.pointId() != null && st == null) throw bad("Elegí una etapa para el punto");
        JourneyPoint linked = null;
        for (JourneyPoint old :
                em.createQuery(
                                "select p from JourneyPoint p where p.coupleId=:couple",
                                JourneyPoint.class)
                        .setParameter("couple", couple())
                        .getResultList()) {
            SourceRef oldRef = ref(old);
            if (oldRef != null
                    && oldRef.section().equals(section)
                    && experience.equals(oldRef.experienceId())) {
                trip(old.journeyId, true);
                if (st != null
                        && old.stageId.equals(st.id)
                        && (r.pointId() == null || old.id.equals(r.pointId()))) linked = old;
                else {
                    boolean associated =
                            movements.findByJourneyIdAndCoupleId(old.journeyId, couple()).stream()
                                            .anyMatch(m -> old.id.equals(m.pointId))
                                    || files
                                            .findSummariesByJourneyIdAndCoupleId(
                                                    old.journeyId, couple())
                                            .stream()
                                            .anyMatch(f -> old.id.equals(f.getPointId()));
                    if (associated)
                        throw conflict(
                                "Reubicá los archivos y movimientos del punto antes de cambiar su"
                                    + " viaje");
                    setSource(old, new SourceRef(section, entity, null));
                    old.status = "PENDING";
                    points.save(old);
                }
            }
        }
        sources.locate(section, experience, city, st == null ? null : st.id);
        if (st != null) {
            JourneyPoint p =
                    linked != null
                            ? linked
                            : r.pointId() == null
                                    ? points
                                            .findByJourneyIdAndCoupleId(st.journeyId, couple())
                                            .stream()
                                            .filter(
                                                    v -> {
                                                        SourceRef x = ref(v);
                                                        return v.stageId.equals(st.id)
                                                                && x != null
                                                                && x.section().equals(section)
                                                                && x.entityId().equals(entity)
                                                                && x.experienceId() == null
                                                                && v.status.equals("PENDING");
                                                    })
                                            .findFirst()
                                            .orElseGet(JourneyPoint::new)
                                    : owned(points, r.pointId());
            if (p.id != null && (!p.stageId.equals(st.id) || !p.journeyId.equals(st.journeyId)))
                throw bad("El punto pertenece a otra etapa");
            SourceRef current = ref(p);
            if (current != null
                    && (!current.section().equals(section)
                            || !current.entityId().equals(entity)
                            || (current.experienceId() != null
                                    && !current.experienceId().equals(experience))))
                throw conflict("El punto ya corresponde a otra experiencia");
            p.journeyId = st.journeyId;
            p.stageId = st.id;
            p.title = p.title == null ? source.title() : p.title;
            p.scheduledOn = exp.date();
            p.status =
                    exp.date() != null
                                    && exp.date()
                                            .isAfter(
                                                    LocalDate.now(
                                                            ZoneId.of(
                                                                    "America/Argentina/Buenos_Aires")))
                            ? "PENDING"
                            : "COMPLETED";
            setSource(p, new SourceRef(section, entity, experience));
            points.saveAndFlush(p);
            return new ExperienceLocation(city, st.id, p.id, st.journeyId);
        }
        return new ExperienceLocation(city, null, null, null);
    }

    @Transactional
    public LinkedDateDto linkDate(UUID tripId, DateLinkRequest r, User actor) {
        Journey journey = trip(tripId, true);
        JourneyStage st = stage(tripId, r.stageId());
        LocalDate rangeEnd = r.endsOn() == null ? r.date() : r.endsOn();
        dates(r.date(), rangeEnd, journey.startsOn, journey.endsOn);
        if (!r.date().equals(journey.startsOn) || !rangeEnd.equals(journey.endsOn))
            throw bad("La fecha importante debe abarcar el período completo del viaje");
        if (!st.startsOn.equals(journey.startsOn) || !st.endsOn.equals(journey.endsOn))
            throw bad("La fecha importante debe abarcar el período completo del viaje");
        sources.memberId(actor.id);
        SpecialDate template;
        if (r.specialDateId() != null)
            template =
                    dateTemplates
                            .findLockedByIdAndCoupleId(r.specialDateId(), couple())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "Fecha no encontrada"));
        else {
            if (r.label() == null || r.label().isBlank())
                throw bad("Escribí el nombre de la fecha");
            template = new SpecialDate();
            template.date = journey.startsOn;
            template.endsOn = journey.endsOn;
            template.label = r.label().trim();
            template.recurrence = SpecialDateRecurrence.ONCE;
            template.createdAt = template.updatedAt = Instant.now();
            dateTemplates.save(template);
            em.flush();
        }
        if (template.date == null) throw bad("La fecha importante no tiene una fecha de inicio");
        SpecialDateOccurrence o =
                dateOccurrences
                        .findDetailedBySpecialDateIdAndOccurredOnAndCoupleId(
                                template.id, journey.startsOn, couple())
                        .orElseGet(SpecialDateOccurrence::new);
        if (o.stageId != null && !o.stageId.equals(st.id))
            throw conflict("Esta fecha ya está vinculada a otro viaje; cambiala desde WhenDates");
        if (o.id == null) {
            o.specialDate = template;
            o.occurredOn = journey.startsOn;
            o.createdBy = actor;
            o.createdAt = Instant.now();
        }
        o.endsOn = journey.endsOn;
        o.cityId = st.cityId;
        o.stageId = st.id;
        o.updatedBy = actor;
        o.updatedAt = Instant.now();
        dateOccurrences.save(o);
        return new LinkedDateDto(template.id, journey.startsOn, journey.endsOn, template.label, st.id);
    }

    public void validateCatalogCity(String section, Long entity, Long city) {
        if (entity != null && city != null && sources.invalidPhysicalCity(section, entity, city))
            throw conflict(
                    "Reubicá las experiencias y puntos antes de cambiar la ciudad del lugar");
    }

    @Transactional
    public FileDto relinkFile(UUID id, FileLinksRequest r) {
        JourneyFile f = owned(files, id);
        trip(f.journeyId, true);
        associations(f.journeyId, r.stageId(), r.pointId(), r.stayId(), r.movementId());
        UUID stage = associationStage(r.stageId(), r.pointId(), r.stayId(), r.movementId());
        f.stageId = stage;
        f.pointId = r.pointId();
        f.stayId = r.stayId();
        f.movementId = r.movementId();
        files.save(f);
        return fileDto(f);
    }

    @Transactional
    public void refreshExperience(LocatedExperience experience) {
        em.refresh(experience);
    }

    @Transactional
    public void beforeExperienceDelete(String section, Long experience) {
        em.flush();
        for (JourneyPoint p :
                em.createQuery(
                                "select p from JourneyPoint p where p.coupleId=:couple",
                                JourneyPoint.class)
                        .setParameter("couple", couple())
                        .getResultList()) {
            SourceRef source = ref(p);
            if (source != null
                    && source.section().equals(section)
                    && experience.equals(source.experienceId())) {
                trip(p.journeyId, true);
                setSource(p, new SourceRef(section, source.entityId(), null));
                p.status = "PENDING";
                points.save(p);
            }
        }
        em.flush();
    }

    @Transactional
    public void pending(String section, Long entity, UUID stageId) {
        if (stageId == null) return;
        em.flush();
        JourneyStage s = owned(stages, stageId);
        SourceDto source = sources.source(section, entity);
        savePoint(
                s.journeyId,
                null,
                new PointRequest(
                        stageId,
                        source.title(),
                        null,
                        null,
                        null,
                        null,
                        points.findByJourneyIdAndCoupleId(s.journeyId, couple()).size(),
                        "PENDING",
                        new SourceRef(section, entity, null)));
    }

    @Transactional
    public void locateNew(
            LocatedExperience exp,
            Long defaultCity,
            Long requestedCity,
            UUID stageId,
            LocalDate date) {
        Long city = requestedCity == null ? defaultCity : requestedCity;
        if (city == null) city = locations.origin();
        locations.city(city);
        if (stageId != null) {
            JourneyStage s = owned(stages, stageId);
            trip(s.journeyId, true);
            if (requestedCity != null && !requestedCity.equals(s.cityId))
                throw bad("La ciudad no coincide con la etapa");
            city = s.cityId;
            dates(date, date, s.startsOn, s.endsOn);
        }
        exp.cityId = city;
        exp.stageId = stageId;
    }
}
