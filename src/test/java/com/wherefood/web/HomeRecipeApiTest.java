package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.OrderColumn;
import org.junit.jupiter.api.Test;

class HomeRecipeApiTest {
 @Test
 void letsAnyAuthenticatedUserUpdateACooking() {
   Recipes recipes = mock(Recipes.class); Cookings cookings = mock(Cookings.class); CookingReviews reviews = mock(CookingReviews.class);
  User tomas = user(7L, "tomas"), avril = user(6L, "avril"); Recipe recipe = new Recipe(); recipe.id = 3L; recipe.name = "Panes rellenos"; recipe.createdBy = recipe.updatedBy = tomas;
  Cooking cooking = new Cooking(); cooking.id = 2L; cooking.recipe = recipe; cooking.home = Home.TOMAS; cooking.servings = 2; cooking.cookedOn = LocalDate.of(2026, 7, 18); cooking.mealType = MealType.CENA; cooking.createdBy = cooking.updatedBy = tomas;
   when(cookings.findDetailedById(2L)).thenReturn(Optional.of(cooking)); when(cookings.save(cooking)).thenReturn(cooking); when(reviews.findByCookingIdOrderByAuthorUsername(2L)).thenReturn(List.of());

   CookingDto result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), cookings, reviews, null).updateCooking(2L, new CookingRequest(Home.AVRIL, 4, LocalDate.of(2026, 7, 21), MealType.ALMUERZO), avril);

   assertEquals(Home.AVRIL, result.home()); assertEquals("avril", result.updatedBy()); verify(cookings).save(cooking); verify(recipes).save(recipe);
 }

 @Test
 void createsAReusableRecipeDefinition() {
  Recipes recipes = mock(Recipes.class); User tomas = user(7L, "tomas"); when(recipes.save(any(Recipe.class))).thenAnswer(invocation -> { Recipe value = invocation.getArgument(0); value.id = 5L; return value; });

   RecipeDto result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), null, null, null).addRecipe(new RecipeRequest("Tarta", "https://example.test/tarta", List.of(new RecipeIngredientRequest("Harina", BigDecimal.valueOf(250), "g")), List.of(new RecipeStepRequest("Hornear."))), tomas);

  assertEquals(5L, result.id()); assertEquals("Tarta", result.name()); assertEquals(1, result.ingredients().size()); assertEquals("tomas", result.createdBy());
 }

 @Test
 void listsRecipesWithIngredientsAndStepsUsingIndexedCollections() throws NoSuchFieldException {
  Recipes recipes = mock(Recipes.class); User tomas = user(7L, "tomas"); Recipe recipe = new Recipe(); recipe.id = 5L; recipe.name = "Tarta"; recipe.createdBy = recipe.updatedBy = tomas; recipe.updatedAt = Instant.parse("2026-07-23T00:00:00Z");
  RecipeIngredient ingredient = new RecipeIngredient(); ingredient.name = "Harina"; ingredient.quantity = BigDecimal.valueOf(250); ingredient.unit = "g"; ingredient.position = 0; recipe.ingredients.add(ingredient);
  RecipeStep step = new RecipeStep(); step.instruction = "Hornear."; step.position = 0; recipe.steps.add(step);
  when(recipes.findAll()).thenReturn(List.of(recipe));

   List<RecipeDto> result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), null, null, null).listRecipes(null);

  assertEquals("Harina", result.getFirst().ingredients().getFirst().name()); assertEquals("Hornear.", result.getFirst().steps().getFirst().instruction());
  assertEquals("position", Recipe.class.getDeclaredField("ingredients").getAnnotation(OrderColumn.class).name());
   assertEquals("position", Recipe.class.getDeclaredField("steps").getAnnotation(OrderColumn.class).name());
  }

  @Test
  void projectsTheRecipeProfileSeparatelyFromCookings() {
   Recipes recipes = mock(Recipes.class); RecipePhotos profilePhotos = mock(RecipePhotos.class); User tomas = user(7L, "tomas");
   Recipe recipe = new Recipe(); recipe.id = 5L; recipe.name = "Tarta"; recipe.createdBy = recipe.updatedBy = tomas; recipe.updatedAt = Instant.parse("2026-07-23T00:00:00Z");
   RecipePhoto photo = new RecipePhoto(); photo.id = 12L; photo.recipe = recipe; photo.width = 1200; photo.height = 800;
   when(recipes.findAll()).thenReturn(List.of(recipe)); when(profilePhotos.findByRecipeId(5L)).thenReturn(Optional.of(photo));

   RecipeDto result = new HomeRecipeApi(recipes, profilePhotos, null, null, null).listRecipes(null).getFirst();

   assertEquals("/how-cook/recipes/5/photo?v=12", result.photoUrl());
   assertEquals("/how-cook/recipes/5/photo?thumbnail=true&v=12", result.thumbnailUrl());
   assertEquals(1200, result.photoWidth());
   assertEquals(800, result.photoHeight());
  }

 private static User user(Long id, String username) { User user = new User(); user.id = id; user.username = username; return user; }
}
