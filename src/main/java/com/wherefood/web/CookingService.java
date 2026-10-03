package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Cooking;
import com.wherefood.domain.Recipe;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Cookings;
import com.wherefood.repo.Repositories.Recipes;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns dated cooking executions and their recipe summary updates. */
@Service
@Transactional(readOnly = true)
public class CookingService {
    private final Recipes recipes;
    private final Cookings cookings;
    private final CoupleAuthorizationService authorization;
    private final com.wherefood.journey.JourneyService journey;

    public CookingService(Recipes recipes, Cookings cookings, CoupleAuthorizationService authorization) { this(recipes, cookings, authorization, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public CookingService(Recipes recipes, Cookings cookings, CoupleAuthorizationService authorization, com.wherefood.journey.JourneyService journey) {
        this.recipes = recipes;
        this.cookings = cookings;
        this.authorization = authorization;
        this.journey = journey;
    }

    @Transactional
    public Cooking create(Long recipeId, CookingRequest request, User actor) {
        authorization.requireActiveMember(actor);
        validateDate(request);
        Recipe recipe = findRecipe(recipeId);
        Cooking cooking = new Cooking();
        cooking.recipe = recipe;
        cooking.createdBy = cooking.updatedBy = actor;
        cooking.createdAt = cooking.updatedAt = Instant.now();
        apply(cooking, request);
        touch(recipe, actor);
        if (journey != null) journey.locateNew(cooking, cooking.cityId == null ? cooking.recipe.zoneId : cooking.cityId, request.cityId(), request.stageId() == null && request.cityId() == null ? cooking.stageId : request.stageId(), request.cookedOn()); else if (cooking.cityId == null) cooking.cityId = cooking.recipe.zoneId;
        Cooking saved = cookings.save(cooking);
        if (journey != null) journey.bind("COOK", saved.recipe.id, saved.id, new com.wherefood.journey.JourneyDtos.BindingRequest(saved.cityId, saved.stageId, request.pointId()));
        if (journey != null) journey.refreshExperience(saved);
        return saved;
    }

    @Transactional
    public Cooking update(Long cookingId, CookingRequest request, User actor) {
        authorization.requireActiveMember(actor);
        validateDate(request);
        Cooking cooking = findCooking(cookingId);
        apply(cooking, request);
        cooking.updatedBy = actor;
        cooking.updatedAt = Instant.now();
        touch(cooking.recipe, actor);
        if (journey != null) journey.locateNew(cooking, cooking.cityId == null ? cooking.recipe.zoneId : cooking.cityId, request.cityId(), request.stageId() == null && request.cityId() == null ? cooking.stageId : request.stageId(), request.cookedOn()); else if (cooking.cityId == null) cooking.cityId = cooking.recipe.zoneId;
        Cooking saved = cookings.save(cooking);
        if (journey != null) journey.bind("COOK", saved.recipe.id, saved.id, new com.wherefood.journey.JourneyDtos.BindingRequest(saved.cityId, saved.stageId, request.pointId()));
        if (journey != null) journey.refreshExperience(saved);
        return saved;
    }

    @Transactional
    public void delete(Long cookingId, User actor) {
        authorization.requireActiveMember(actor);
        Cooking cooking = findCooking(cookingId);
        if (journey != null) journey.beforeExperienceDelete("COOK", cooking.id);
        cookings.delete(cooking);
        touch(cooking.recipe, actor);
    }

    private Recipe findRecipe(Long recipeId) {
        return recipes.findByIdAndCoupleId(recipeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receta no encontrada"));
    }

    private Cooking findCooking(Long cookingId) {
        return cookings.findDetailedByIdAndCoupleId(cookingId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Preparación no encontrada"));
    }

    private void touch(Recipe recipe, User actor) {
        recipe.updatedBy = actor;
        recipe.updatedAt = Instant.now();
        recipes.save(recipe);
    }

    private static void validateDate(CookingRequest request) {
        if (request.cookedOn().isAfter(RosarioClock.today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una preparación no puede quedar en el futuro");
        }
    }

    private static void apply(Cooking cooking, CookingRequest request) {
        cooking.home = request.home();
        cooking.servings = request.servings();
        cooking.cookedOn = request.cookedOn();
        cooking.mealType = request.mealType();
    }
}
