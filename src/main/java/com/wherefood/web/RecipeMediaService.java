package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Recipe;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.RecipePhotos;
import com.wherefood.repo.Repositories.Recipes;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Couple-scoped recipe profile-media replacement. */
@Service
@Transactional(readOnly = true)
public class RecipeMediaService {
    private final Recipes recipes;
    private final RecipePhotos photos;
    private final PhotoStorage storage;
    private final CoupleAuthorizationService authorization;

    public RecipeMediaService(Recipes recipes, RecipePhotos photos, PhotoStorage storage,
            CoupleAuthorizationService authorization) {
        this.recipes = recipes;
        this.photos = photos;
        this.storage = storage;
        this.authorization = authorization;
    }

    @Transactional
    public Recipe replacePhoto(Long recipeId, MultipartFile file, User actor) throws IOException {
        authorization.requireActiveMember(actor);
        Recipe recipe = recipes.findByIdAndCoupleId(recipeId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receta no encontrada"));
        photos.findByRecipeIdAndCoupleId(recipeId, CoupleContext.current()).ifPresent(photos::delete);
        photos.flush();
        recipe.updatedBy = actor;
        recipe.updatedAt = Instant.now();
        recipes.save(recipe);
        photos.save(storage.store(recipe, file));
        return recipe;
    }
}
