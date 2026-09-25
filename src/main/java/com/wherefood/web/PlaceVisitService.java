package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceStatus;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns visit mutations and their parent-place state in one couple-scoped transaction. */
@Service
@Transactional(readOnly = true)
public class PlaceVisitService {
    private final Places places;
    private final PlaceVisits visits;
    private final CoupleAuthorizationService authorization;

    public PlaceVisitService(Places places, PlaceVisits visits, CoupleAuthorizationService authorization) {
        this.places = places;
        this.visits = visits;
        this.authorization = authorization;
    }

    @Transactional
    public PlaceVisit create(Long placeId, VisitRequest request, User actor) {
        authorization.requireActiveMember(actor);
        validateDate(request);
        Place place = findActivePlace(placeId);
        if (visits.findByPlaceIdAndVisitedOn(placeId, request.visitedOn()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una visita para esa fecha");
        }
        PlaceVisit visit = new PlaceVisit();
        visit.place = place;
        visit.visitedOn = request.visitedOn();
        visit.createdBy = visit.updatedBy = actor;
        visit.createdAt = visit.updatedAt = Instant.now();
        place.status = PlaceStatus.REVIEWED;
        touch(place, actor);
        return visits.save(visit);
    }

    @Transactional
    public PlaceVisit update(Long visitId, VisitRequest request, User actor) {
        authorization.requireActiveMember(actor);
        validateDate(request);
        PlaceVisit visit = findActiveVisit(visitId);
        visits.findByPlaceIdAndVisitedOn(visit.place.id, request.visitedOn())
                .filter(other -> !other.id.equals(visit.id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una visita para esa fecha"); });
        visit.visitedOn = request.visitedOn();
        visit.updatedBy = actor;
        visit.updatedAt = Instant.now();
        touch(visit.place, actor);
        return visits.save(visit);
    }

    @Transactional
    public void delete(Long visitId, User actor) {
        authorization.requireActiveMember(actor);
        PlaceVisit visit = findActiveVisit(visitId);
        Place place = visit.place;
        visits.delete(visit);
        if (!visits.existsByPlaceId(place.id)) place.status = PlaceStatus.PENDING;
        touch(place, actor);
    }

    private Place findActivePlace(Long placeId) {
        Place place = places.findByIdAndCoupleId(placeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lugar no encontrado"));
        if (place.deactivatedAt != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lugar no encontrado");
        }
        return place;
    }

    private PlaceVisit findActiveVisit(Long visitId) {
        PlaceVisit visit = visits.findDetailedByIdAndCoupleId(visitId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Visita no encontrada"));
        if (visit.place == null || visit.place.deactivatedAt != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada");
        }
        return visit;
    }

    private void touch(Place place, User actor) {
        place.updatedBy = actor;
        place.updatedAt = Instant.now();
        places.save(place);
    }

    private static void validateDate(VisitRequest request) {
        if (request.visitedOn().isAfter(RosarioClock.today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una visita no puede quedar en el futuro");
        }
    }
}
