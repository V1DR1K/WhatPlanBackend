package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Recipe;
import com.wherefood.domain.RecipeIngredient;
import com.wherefood.domain.RecipeStep;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Cookings;
import com.wherefood.repo.Repositories.Recipes;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns couple-scoped writes and child invariants for the reusable recipe aggregate. */
@Service
@Transactional(readOnly = true)
public class RecipeService {
    private final Recipes recipes;
    private final Cookings cookings;
    private final CoupleAuthorizationService authorization;

    public RecipeService(Recipes recipes, Cookings cookings, CoupleAuthorizationService authorization) {
        this.recipes = recipes;
        this.cookings = cookings;
        this.authorization = authorization;
    }

    @Transactional
    public Recipe create(RecipeRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Recipe recipe = new Recipe();
        recipe.createdBy = recipe.updatedBy = actor;
        recipe.createdAt = recipe.updatedAt = Instant.now();
        apply(recipe, request);
        return recipes.save(recipe);
    }

    @Transactional
    public Recipe update(Long recipeId, RecipeRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Recipe recipe = findRecipe(recipeId);
        apply(recipe, request);
        recipe.updatedBy = actor;
        recipe.updatedAt = Instant.now();
        return recipes.save(recipe);
    }

    @Transactional
    public void delete(Long recipeId, User actor) {
        authorization.requireActiveMember(actor);
        Recipe recipe = findRecipe(recipeId);
        if (cookings.existsByRecipeIdAndCoupleId(recipe.id, CoupleContext.current())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés borrar una receta con preparaciones");
        }
        recipes.delete(recipe);
    }

    private Recipe findRecipe(Long recipeId) {
        return recipes.findByIdAndCoupleId(recipeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receta no encontrada"));
    }

    private static void apply(Recipe recipe, RecipeRequest request) {
        recipe.name = request.name().trim();
        recipe.sourceUrl = blankToNull(request.sourceUrl());
        recipe.ingredients.clear();
        recipe.steps.clear();
        List<RecipeIngredientRequest> ingredients = request.ingredients() == null
                ? List.of() : request.ingredients();
        List<RecipeStepRequest> steps = request.steps() == null ? List.of() : request.steps();
        for (int position = 0; position < ingredients.size(); position++) {
            RecipeIngredientRequest source = ingredients.get(position);
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.recipe = recipe;
            ingredient.name = source.name().trim();
            ingredient.quantity = source.quantity();
            ingredient.unit = source.unit().trim();
            ingredient.position = position;
            recipe.ingredients.add(ingredient);
        }
        for (int position = 0; position < steps.size(); position++) {
            RecipeStepRequest source = steps.get(position);
            RecipeStep step = new RecipeStep();
            step.recipe = recipe;
            step.instruction = source.instruction().trim();
            step.position = position;
            recipe.steps.add(step);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
