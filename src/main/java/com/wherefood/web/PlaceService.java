package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.application.PlaceInput;
import com.wherefood.domain.Category;
import com.wherefood.domain.HighlightTag;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceStatus;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Categories;
import com.wherefood.repo.Repositories.HighlightTags;
import com.wherefood.repo.Repositories.Places;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns the shared place aggregate; roots are always loaded in the member's active couple. */
@Service
@Transactional(readOnly = true)
public class PlaceService {
    private final Places places;
    private final Categories categories;
    private final HighlightTags highlightTags;
    private final CoupleAuthorizationService authorization;
    private final ZoneSettingsService zoneSettings;
    private final com.wherefood.journey.JourneyService journey;

    public PlaceService(Places places, Categories categories, HighlightTags highlightTags,
            CoupleAuthorizationService authorization) {
        this(places, categories, highlightTags, authorization, null);
    }

    public PlaceService(Places places, Categories categories, HighlightTags highlightTags,
            CoupleAuthorizationService authorization, ZoneSettingsService zoneSettings) { this(places, categories, highlightTags, authorization, zoneSettings, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public PlaceService(Places places, Categories categories, HighlightTags highlightTags,
            CoupleAuthorizationService authorization, ZoneSettingsService zoneSettings, com.wherefood.journey.JourneyService journey) {
        this.places = places;
        this.categories = categories;
        this.highlightTags = highlightTags;
        this.authorization = authorization;
        this.zoneSettings = zoneSettings;
        this.journey = journey;
    }

    @Transactional
    public Place create(PlaceInput request, User actor) {
        authorization.requireActiveMember(actor);
        Place place = new Place();
        apply(place, request);
        place.category = categories.findById(request.categoryId()).filter(value -> value.active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría no encontrada"));
        place.status = PlaceStatus.PENDING;
        place.createdBy = place.updatedBy = actor;
        place.createdAt = place.updatedAt = Instant.now();
        Place saved = places.save(place);
        if (journey != null) journey.pending("FOOD", saved.id, request.stageId());
        return saved;
    }

    @Transactional
    public Place update(Long placeId, PlaceInput request, User actor) {
        authorization.requireActiveMember(actor);
        Place place = findActive(placeId);
        Category selectedCategory = categories.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría no encontrada"));
        if (!selectedCategory.active && (place.category == null || !selectedCategory.id.equals(place.category.id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada");
        }
        apply(place, request);
        place.category = selectedCategory;
        place.updatedBy = actor;
        place.updatedAt = Instant.now();
        return places.save(place);
    }

    @Transactional
    public Place archive(Long placeId, User actor) {
        authorization.requireActiveMember(actor);
        Place place = findActive(placeId);
        place.deactivatedAt = place.updatedAt = Instant.now();
        place.updatedBy = actor;
        return places.save(place);
    }

    @Transactional
    public Place restore(Long placeId, User actor) {
        authorization.requireActiveMember(actor);
        Place place = places.findDetailedByIdAndCoupleId(placeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lugar no encontrado"));
        place.deactivatedAt = null;
        place.updatedBy = actor;
        place.updatedAt = Instant.now();
        return places.save(place);
    }

    private Place findActive(Long placeId) {
        Place place = places.findDetailedByIdAndCoupleId(placeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lugar no encontrado"));
        if (place.deactivatedAt != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lugar no encontrado");
        }
        return place;
    }

    private void apply(Place place, PlaceInput request) {
        if (request.zoneId() != null) {
            if (journey != null) journey.validateCatalogCity("FOOD", place.id, request.zoneId());
            if (zoneSettings != null) zoneSettings.requireActive(request.zoneId());
            place.zoneId = request.zoneId();
        } else if (place.zoneId == null) {
            if (zoneSettings != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una Zona para el registro");
            place.zoneId = 1L;
        }
        place.name = request.name();
        place.address = request.address();
        place.sourceUrl = request.sourceUrl();
        place.mapsUrl = request.mapsUrl();
        place.acceptsReservations = request.acceptsReservations();
        if (request.tagIds() == null || request.tagIds().isEmpty()) {
            place.highlightTags.clear();
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(request.tagIds());
        List<HighlightTag> selected = highlightTags.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada");
        }
        Set<Long> existingIds = place.highlightTags.stream().map(tag -> tag.id).collect(java.util.stream.Collectors.toSet());
        if (selected.stream().anyMatch(tag -> !tag.active && !existingIds.contains(tag.id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada");
        }
        place.highlightTags.clear();
        place.highlightTags.addAll(selected);
    }
}
