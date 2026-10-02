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

    public CookingService(Recipes recipes, Cookings cookings, CoupleAuthorizationService authorization) {
        this.recipes = recipes;
        this.cookings = cookings;
        this.authorization = authorization;
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
        return cookings.save(cooking);
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
        return cookings.save(cooking);
    }

    @Transactional
    public void delete(Long cookingId, User actor) {
        authorization.requireActiveMember(actor);
        Cooking cooking = findCooking(cookingId);
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
