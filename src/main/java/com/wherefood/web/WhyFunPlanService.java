package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunCategory;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.domain.WhyFunVenueReview;
import com.wherefood.domain.WhyFunVenueSchedule;
import com.wherefood.repo.Repositories.WhyFunCategories;
import com.wherefood.repo.Repositories.WhyFunVenueReviews;
import com.wherefood.repo.Repositories.WhyFunVenues;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Application operations for legacy reusable plans, backed by couple-scoped aggregates. */
@Service
@Transactional(readOnly = true)
public class WhyFunPlanService {
    private final WhyFunCategories categories;
    private final WhyFunVenues venues;
    private final WhyFunVenueReviews reviews;
    private final CoupleAuthorizationService authorization;
    private final ZoneSettingsService zoneSettings;
    private final com.wherefood.journey.JourneyService journey;

    public WhyFunPlanService(WhyFunCategories categories, WhyFunVenues venues,
            WhyFunVenueReviews reviews, CoupleAuthorizationService authorization) {
        this(categories, venues, reviews, authorization, null);
    }

    public WhyFunPlanService(WhyFunCategories categories, WhyFunVenues venues,
            WhyFunVenueReviews reviews, CoupleAuthorizationService authorization,
            ZoneSettingsService zoneSettings) { this(categories, venues, reviews, authorization, zoneSettings, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public WhyFunPlanService(WhyFunCategories categories, WhyFunVenues venues,
            WhyFunVenueReviews reviews, CoupleAuthorizationService authorization,
            ZoneSettingsService zoneSettings, com.wherefood.journey.JourneyService journey) {
        this.categories = categories;
        this.venues = venues;
        this.reviews = reviews;
        this.authorization = authorization;
        this.zoneSettings = zoneSettings;
        this.journey = journey;
    }

    @Transactional
    public WhyFunVenue create(FunPlanRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue plan = new WhyFunVenue();
        plan.createdBy = plan.updatedBy = actor;
        plan.createdAt = plan.updatedAt = Instant.now();
        apply(plan, request);
        WhyFunVenue saved = venues.save(plan);
        if (journey != null) journey.pending("FUN", saved.id, request.stageId());
        return saved;
    }

    @Transactional
    public WhyFunVenue update(Long id, FunPlanRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue plan = findPlan(id);
        apply(plan, request);
        plan.updatedBy = actor;
        plan.updatedAt = Instant.now();
        return venues.save(plan);
    }

    @Transactional
    public void delete(Long id, User actor) {
        authorization.requireActiveMember(actor);
        venues.delete(findPlan(id));
    }

    @Transactional
    public WhyFunVenueReview saveOwnReview(Long planId, FunReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue plan = findPlan(planId);
        WhyFunVenueReview review = reviews.findByVenueIdAndAuthorIdAndCoupleId(planId, actor.id,
                CoupleContext.current()).orElseGet(() -> {
            WhyFunVenueReview value = new WhyFunVenueReview();
            value.venue = plan;
            value.author = actor;
            value.createdAt = Instant.now();
            return value;
        });
        review.rating = request.rating();
        review.comment = request.comment() == null || request.comment().isBlank()
                ? null : request.comment();
        review.updatedAt = Instant.now();
        return reviews.save(review);
    }

    private WhyFunVenue findPlan(Long id) {
        return venues.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan no encontrado"));
    }

    private WhyFunCategory findCategory(Long id) {
        return categories.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría no encontrada"));
    }

    private void apply(WhyFunVenue plan, FunPlanRequest request) {
        if (request.zoneId() != null) {
            if (zoneSettings != null) zoneSettings.requireActive(request.zoneId());
            plan.zoneId = request.zoneId();
        } else if (plan.zoneId == null) {
            if (zoneSettings != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una Zona para el registro");
            plan.zoneId = 1L;
        }
        WhyFunCategory category = findCategory(request.categoryId());
        WhyFunCategory subcategory = findCategory(request.subcategoryId());
        boolean preservesInactiveCategory = plan.id != null && plan.category != null
                && plan.category.id.equals(category.id);
        boolean preservesInactiveSubcategory = plan.id != null && plan.subcategory != null
                && plan.subcategory.id.equals(subcategory.id);
        if ((!category.active && !preservesInactiveCategory) || category.parent != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una categoría principal activa");
        }
        if ((!subcategory.active && !preservesInactiveSubcategory) || subcategory.parent == null
                || !subcategory.parent.id.equals(category.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una subcategoría activa de la categoría seleccionada");
        }
        plan.name = request.name().trim();
        plan.address = request.address().trim();
        plan.scheduledAt = request.scheduledAt();
        plan.category = category;
        plan.subcategory = subcategory;
        plan.schedules.clear();
        if (plan.id != null) venues.flush();
        if (request.schedules() == null) return;
        for (ActivityScheduleRequest source : request.schedules()) {
            if (source.opensAt().equals(source.closesAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El horario de apertura y cierre debe ser distinto");
            }
            WhyFunVenueSchedule schedule = new WhyFunVenueSchedule();
            schedule.venue = plan;
            schedule.dayOfWeek = source.dayOfWeek();
            schedule.opensAt = source.opensAt();
            schedule.closesAt = source.closesAt();
            plan.schedules.add(schedule);
        }
    }
}
