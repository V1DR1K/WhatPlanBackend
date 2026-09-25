package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.domain.WhyFunVenuePhoto;
import com.wherefood.domain.WhyFunVisit;
import com.wherefood.domain.WhyFunVisitPhoto;
import com.wherefood.repo.Repositories.WhyFunVenuePhotos;
import com.wherefood.repo.Repositories.WhyFunVenues;
import com.wherefood.repo.Repositories.WhyFunVisitPhotos;
import com.wherefood.repo.Repositories.WhyFunVisits;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Couple-scoped photo writes for activity and activity-visit aggregates. */
@Service
@Transactional(readOnly = true)
public class WhyFunMediaService {
    private static final int MAX_VISIT_PHOTOS = 4;
    private static final int MAX_PLAN_PHOTOS = 12;
    private final WhyFunVenues venues;
    private final WhyFunVenuePhotos venuePhotos;
    private final WhyFunVisits visits;
    private final WhyFunVisitPhotos visitPhotos;
    private final PhotoStorage storage;
    private final CoupleAuthorizationService authorization;

    public WhyFunMediaService(WhyFunVenues venues, WhyFunVenuePhotos venuePhotos,
            WhyFunVisits visits, WhyFunVisitPhotos visitPhotos, PhotoStorage storage,
            CoupleAuthorizationService authorization) {
        this.venues = venues;
        this.venuePhotos = venuePhotos;
        this.visits = visits;
        this.visitPhotos = visitPhotos;
        this.storage = storage;
        this.authorization = authorization;
    }

    @Transactional
    public WhyFunVenue uploadVenuePhoto(Long venueId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        WhyFunVenue venue = findVenue(venueId);
        List<WhyFunVenuePhoto> current = venuePhotos.findByVenueIdAndCoupleIdOrderByIdAsc(venueId,
                CoupleContext.current());
        current.stream().filter(photo -> photo.id.equals(venue.coverPhotoId)).findFirst()
                .or(() -> current.stream().findFirst()).ifPresent(venuePhotos::delete);
        venuePhotos.flush();
        WhyFunVenuePhoto photo = venuePhotos.save(storage.store(venue, file));
        venue.coverPhotoId = photo.id;
        touch(venue, actor);
        return venue;
    }

    @Transactional
    public WhyFunVenue uploadPlanPhoto(Long venueId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        WhyFunVenue venue = findVenue(venueId);
        if (venuePhotos.countByVenueIdAndCoupleId(venueId, CoupleContext.current()) >= MAX_PLAN_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cada plan admite hasta " + MAX_PLAN_PHOTOS + " fotos");
        }
        WhyFunVenuePhoto photo = venuePhotos.save(storage.store(venue, file));
        if (venue.coverPhotoId == null) venue.coverPhotoId = photo.id;
        touch(venue, actor);
        venues.save(venue);
        return venue;
    }

    @Transactional
    public WhyFunVisit uploadVisitPhoto(Long visitId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        List<WhyFunVisitPhoto> current = visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(
                visitId, CoupleContext.current());
        if (current.size() >= MAX_VISIT_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cada visita admite hasta " + MAX_VISIT_PHOTOS + " fotos");
        }
        WhyFunVisitPhoto photo = visitPhotos.save(storage.store(visit, actor,
                current.isEmpty() ? 0 : current.getLast().position + 1, file));
        if (visit.coverPhotoId == null) visit.coverPhotoId = photo.id;
        touch(visit, actor);
        visits.save(visit);
        return visit;
    }

    @Transactional
    public WhyFunVisit setVisitCover(Long visitId, Long photoId, User actor) {
        requireMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        WhyFunVisitPhoto photo = visitPhotos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        if (!photo.visit.id.equals(visit.id)) throw notFound("Foto");
        visit.coverPhotoId = photo.id;
        touch(visit, actor);
        return visits.save(visit);
    }

    @Transactional
    public WhyFunVenue setVenueCover(Long venueId, Long photoId, User actor) {
        requireMember(actor);
        WhyFunVenue venue = findVenue(venueId);
        WhyFunVenuePhoto photo = venuePhotos.findByIdAndVenueIdAndCoupleId(photoId, venueId,
                CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
        venue.coverPhotoId = photo.id;
        touch(venue, actor);
        return venues.save(venue);
    }

    @Transactional
    public void deleteVenuePhoto(Long photoId, User actor) {
        requireMember(actor);
        WhyFunVenuePhoto photo = venuePhotos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        WhyFunVenue venue = findVenue(photo.venue.id);
        if (photo.id.equals(venue.coverPhotoId)) {
            venue.coverPhotoId = null;
            touch(venue, actor);
            venues.save(venue);
        }
        venuePhotos.delete(photo);
    }

    @Transactional
    public void deleteVisitPhoto(Long photoId, User actor) {
        requireMember(actor);
        WhyFunVisitPhoto photo = visitPhotos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        WhyFunVisit visit = findVisit(photo.visit.id);
        boolean wasCover = photo.id.equals(visit.coverPhotoId);
        visitPhotos.delete(photo);
        visitPhotos.flush();
        if (wasCover) visit.coverPhotoId = visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(
                visit.id, CoupleContext.current())
                .stream().findFirst().map(value -> value.id).orElse(null);
        touch(visit, actor);
        visits.save(visit);
    }

    private WhyFunVenue findVenue(Long id) {
        return venues.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> notFound("Actividad"));
    }

    private WhyFunVisit findVisit(Long id) {
        return visits.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> notFound("Visita"));
    }

    private void requireMember(User actor) { authorization.requireActiveMember(actor); }

    private static void touch(WhyFunVenue venue, User actor) {
        venue.updatedBy = actor;
        venue.updatedAt = Instant.now();
    }

    private static void touch(WhyFunVisit visit, User actor) {
        visit.updatedBy = actor;
        visit.updatedAt = Instant.now();
    }

    private static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " no encontrada");
    }
}
