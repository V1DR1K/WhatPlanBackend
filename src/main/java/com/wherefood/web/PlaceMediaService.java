package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Item;
import com.wherefood.domain.ItemPhoto;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlacePhoto;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.Photos;
import com.wherefood.repo.Repositories.PlacePhotos;
import com.wherefood.repo.Repositories.PlaceVisitPhotos;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Enforces membership and aggregate ownership for private place/item media mutations. */
@Service
@Transactional(readOnly = true)
public class PlaceMediaService {
    private static final int MAX_VISIT_PHOTOS = 4;

    private final Places places;
    private final PlaceVisits visits;
    private final Items items;
    private final PlacePhotos placePhotos;
    private final PlaceVisitPhotos visitPhotos;
    private final Photos itemPhotos;
    private final PhotoStorage storage;
    private final CoupleAuthorizationService authorization;

    public PlaceMediaService(Places places, PlaceVisits visits, Items items, PlacePhotos placePhotos,
            PlaceVisitPhotos visitPhotos, Photos itemPhotos, PhotoStorage storage,
            CoupleAuthorizationService authorization) {
        this.places = places;
        this.visits = visits;
        this.items = items;
        this.placePhotos = placePhotos;
        this.visitPhotos = visitPhotos;
        this.itemPhotos = itemPhotos;
        this.storage = storage;
        this.authorization = authorization;
    }

    @Transactional
    public Place uploadPlacePhoto(Long placeId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        Place place = activePlace(placeId);
        placePhotos.findByPlaceIdAndCoupleId(placeId, CoupleContext.current()).ifPresent(placePhotos::delete);
        placePhotos.flush();
        place.updatedBy = actor;
        place.updatedAt = Instant.now();
        places.save(place);
        placePhotos.save(storage.store(place, file));
        return place;
    }

    @Transactional
    public ItemPhoto uploadItemPhoto(Long itemId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        Item item = items.findByIdAndCoupleId(itemId, CoupleContext.current())
                .filter(value -> value.deletedAt == null)
                .orElseThrow(() -> notFound("Ítem"));
        itemPhotos.findByItemIdAndCoupleId(itemId, CoupleContext.current()).ifPresent(itemPhotos::delete);
        itemPhotos.flush();
        ItemPhoto photo = storage.store(item, file);
        return itemPhotos.save(photo);
    }

    @Transactional
    public PlaceVisit uploadVisitPhoto(Long visitId, MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        PlaceVisit visit = activeVisit(visitId);
        List<PlaceVisitPhoto> current = visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(visitId,
                CoupleContext.current());
        if (current.size() >= MAX_VISIT_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cada visita admite hasta " + MAX_VISIT_PHOTOS + " fotos");
        }
        PlaceVisitPhoto photo = visitPhotos.save(storage.store(visit, actor,
                current.isEmpty() ? 0 : current.getLast().position + 1, file));
        if (visit.coverPhotoId == null) {
            visit.coverPhotoId = photo.id;
            touch(visit, actor);
        }
        return visit;
    }

    @Transactional
    public PlaceVisit setVisitCover(Long visitId, Long photoId, User actor) {
        requireMember(actor);
        PlaceVisit visit = activeVisit(visitId);
        PlaceVisitPhoto photo = visitPhotos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        if (!photo.visit.id.equals(visit.id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada");
        }
        visit.coverPhotoId = photo.id;
        touch(visit, actor);
        return visits.save(visit);
    }

    @Transactional
    public void deleteVisitPhoto(Long photoId, User actor) {
        requireMember(actor);
        PlaceVisitPhoto photo = visitPhotos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        PlaceVisit visit = activeVisit(photo.visit.id);
        boolean wasCover = photo.id.equals(visit.coverPhotoId);
        visitPhotos.delete(photo);
        visitPhotos.flush();
        if (wasCover) {
            visit.coverPhotoId = visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(visit.id,
                    CoupleContext.current()).stream()
                    .findFirst().map(value -> value.id).orElse(null);
            touch(visit, actor);
            visits.save(visit);
        }
    }

    private Place activePlace(Long id) {
        return places.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .filter(value -> value.deactivatedAt == null)
                .orElseThrow(() -> notFound("Lugar"));
    }

    private PlaceVisit activeVisit(Long id) {
        visits.findLockedByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> notFound("Visita"));
        return visits.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .filter(value -> value.place.deactivatedAt == null)
                .orElseThrow(() -> notFound("Visita"));
    }

    private void requireMember(User actor) {
        authorization.requireActiveMember(actor);
    }

    private static void touch(PlaceVisit visit, User actor) {
        visit.updatedBy = actor;
        visit.updatedAt = Instant.now();
    }

    private static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " no encontrado");
    }
}
