package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.WhyFunCategory;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.domain.WhyFunVenueSchedule;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.WhyFunCategories;
import com.wherefood.repo.Repositories.WhyFunVenues;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns the activity aggregate, including its category and schedule invariants. */
@Service
@Transactional(readOnly = true)
public class WhyFunActivityService {
    private final WhyFunCategories categories;
    private final WhyFunVenues activities;
    private final CoupleAuthorizationService authorization;
    private final ZoneSettingsService zoneSettings;

    public WhyFunActivityService(WhyFunCategories categories, WhyFunVenues activities,
            CoupleAuthorizationService authorization) {
        this(categories, activities, authorization, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WhyFunActivityService(WhyFunCategories categories, WhyFunVenues activities,
            CoupleAuthorizationService authorization, ZoneSettingsService zoneSettings) {
        this.categories = categories;
        this.activities = activities;
        this.authorization = authorization;
        this.zoneSettings = zoneSettings;
    }

    @Transactional
    public WhyFunVenue create(ActivityRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue activity = new WhyFunVenue();
        activity.createdBy = activity.updatedBy = actor;
        activity.createdAt = activity.updatedAt = Instant.now();
        apply(activity, request);
        return activities.save(activity);
    }

    @Transactional
    public WhyFunVenue update(Long activityId, ActivityRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue activity = findActivity(activityId);
        apply(activity, request);
        activity.updatedBy = actor;
        activity.updatedAt = Instant.now();
        return activities.save(activity);
    }

    @Transactional
    public void delete(Long activityId, User actor) {
        authorization.requireActiveMember(actor);
        activities.delete(findActivity(activityId));
    }

    private WhyFunVenue findActivity(Long activityId) {
        return activities.findDetailedByIdAndCoupleId(activityId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Actividad no encontrada"));
    }

    private WhyFunCategory findCategory(Long categoryId) {
        return categories.findDetailedById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría no encontrada"));
    }

    private void apply(WhyFunVenue activity, ActivityRequest request) {
        if (request.zoneId() != null) {
            if (zoneSettings != null) zoneSettings.requireActive(request.zoneId());
            activity.zoneId = request.zoneId();
        } else if (activity.zoneId == null) {
            if (zoneSettings != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una Zona para el registro");
            activity.zoneId = 1L;
        }
        WhyFunCategory category = findCategory(request.categoryId());
        WhyFunCategory subcategory = findCategory(request.subcategoryId());
        boolean preservesInactiveCategory = activity.id != null && activity.category != null
                && activity.category.id.equals(category.id);
        boolean preservesInactiveSubcategory = activity.id != null && activity.subcategory != null
                && activity.subcategory.id.equals(subcategory.id);
        if (category.parent != null || (!category.active && !preservesInactiveCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una categoría principal activa");
        }
        if (subcategory.parent == null || !subcategory.parent.id.equals(category.id)
                || (!subcategory.active && !preservesInactiveSubcategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una subcategoría activa de la categoría seleccionada");
        }
        activity.name = request.name().trim();
        activity.address = request.address().trim();
        activity.category = category;
        activity.subcategory = subcategory;
        activity.schedules.clear();
        // Flush orphan removals before inserting replacement rows with the same unique schedule key.
        if (activity.id != null) activities.flush();
        if (request.schedules() == null) return;
        for (ActivityScheduleRequest source : request.schedules()) {
            if (source.opensAt().equals(source.closesAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horario de apertura y cierre debe ser distinto");
            }
            WhyFunVenueSchedule schedule = new WhyFunVenueSchedule();
            schedule.venue = activity;
            schedule.dayOfWeek = source.dayOfWeek();
            schedule.opensAt = source.opensAt();
            schedule.closesAt = source.closesAt();
            activity.schedules.add(schedule);
        }
    }
}
