package com.wherefood.repo;

import com.wherefood.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoupleScopedRepositoryContractTest {
    private static final List<Class<?>> PRIVATE_REPOSITORIES = List.of(
            Repositories.SpecialDates.class, Repositories.SpecialDateOccurrences.class,
            Repositories.SpecialDateOccurrenceComments.class, Repositories.SpecialDateOccurrencePhotos.class,
            Repositories.Places.class, Repositories.PlaceVisits.class, Repositories.PlaceVisitPhotos.class,
            Repositories.PlaceVisitReviews.class, Repositories.Items.class, Repositories.Photos.class,
            Repositories.PlaceReviews.class, Repositories.PlacePhotos.class, Repositories.ItemReviews.class,
            Repositories.Films.class, Repositories.FilmPhotos.class, Repositories.FilmReviews.class,
            Repositories.FilmViews.class, Repositories.HomeRecipes.class, Repositories.HomeRecipePhotos.class,
            Repositories.HomeRecipeReviews.class, Repositories.WhyFunVenues.class,
            Repositories.WhyFunVenuePhotos.class, Repositories.WhyFunVenueReviews.class,
            Repositories.WhyFunVisits.class, Repositories.WhyFunVisitPhotos.class,
            Repositories.WhyFunVisitReviews.class, Repositories.Recipes.class,
            Repositories.RecipePhotos.class, Repositories.Cookings.class, Repositories.CookingReviews.class);

    private static final List<Class<?>> PRIVATE_ENTITIES = List.of(
            Cooking.class, CookingReview.class, Film.class, FilmPhoto.class, FilmReview.class, FilmView.class,
            HomeRecipe.class, HomeRecipeIngredient.class, HomeRecipePhoto.class, HomeRecipeReview.class,
            HomeRecipeStep.class, Item.class, ItemPhoto.class, ItemReview.class, Place.class, PlacePhoto.class,
            PlaceReview.class, PlaceVisit.class, PlaceVisitPhoto.class, PlaceVisitReview.class, Recipe.class,
            RecipeIngredient.class, RecipePhoto.class, RecipeStep.class, SpecialDate.class,
            SpecialDateOccurrence.class, SpecialDateOccurrenceComment.class, SpecialDateOccurrencePhoto.class,
            WhyFunVenue.class, WhyFunVenuePhoto.class, WhyFunVenueReview.class, WhyFunVenueSchedule.class,
            WhyFunVisit.class, WhyFunVisitPhoto.class, WhyFunVisitReview.class);

    @Test
    void privateRepositoriesExposeScopedLookupButNoGlobalCrudReads() throws Exception {
        PRIVATE_REPOSITORIES.forEach(repository -> {
            assertDoesNotThrow(() -> repository.getMethod("findByIdAndCoupleId", Long.class, java.util.UUID.class),
                    () -> repository.getSimpleName() + " must expose couple-scoped lookup");
            assertDoesNotThrow(() -> repository.getMethod("findAllByCoupleId", java.util.UUID.class),
                    () -> repository.getSimpleName() + " must expose couple-scoped listing");
            assertThrows(NoSuchMethodException.class, () -> repository.getMethod("findById", Object.class),
                    () -> repository.getSimpleName() + " must not expose global ID lookup");
            assertThrows(NoSuchMethodException.class, () -> repository.getMethod("findAll"),
                    () -> repository.getSimpleName() + " must not expose global listing");
        });
        assertDoesNotThrow(() -> Repositories.Categories.class.getMethod("findAll"),
                "global catalog repositories intentionally retain unscoped access");
    }

    @Test
    void everyPrivateJpaEntityMapsTheTenantKeyFromItsTable() {
        PRIVATE_ENTITIES.forEach(entity -> assertEquals(CoupleScopedEntity.class, entity.getSuperclass(),
                () -> entity.getSimpleName() + " must inherit couple_id mapping"));
    }
}
