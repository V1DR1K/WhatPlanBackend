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

   Slice<RecipeDto> result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), null, null, null).listRecipes(null, null, null, null, null, 5);

   assertEquals("Harina", result.content().getFirst().ingredients().getFirst().name()); assertEquals("Hornear.", result.content().getFirst().steps().getFirst().instruction());
  assertEquals("position", Recipe.class.getDeclaredField("ingredients").getAnnotation(OrderColumn.class).name());
   assertEquals("position", Recipe.class.getDeclaredField("steps").getAnnotation(OrderColumn.class).name());
  }

  @Test
  void projectsTheRecipeProfileSeparatelyFromCookings() {
   Recipes recipes = mock(Recipes.class); RecipePhotos profilePhotos = mock(RecipePhotos.class); User tomas = user(7L, "tomas");
   Recipe recipe = new Recipe(); recipe.id = 5L; recipe.name = "Tarta"; recipe.createdBy = recipe.updatedBy = tomas; recipe.updatedAt = Instant.parse("2026-07-23T00:00:00Z");
   when(recipes.findAll()).thenReturn(List.of(recipe)); when(profilePhotos.metadataByRecipeIdIn(any())).thenReturn(List.of(photo(12L, 5L, 1200, 800)));

   RecipeDto result = new HomeRecipeApi(recipes, profilePhotos, null, null, null).listRecipes(null, null, null, null, null, 5).content().getFirst();

   assertEquals("/how-cook/recipes/5/photo?v=12", result.photoUrl());
   assertEquals("/how-cook/recipes/5/photo?thumbnail=true&v=12", result.thumbnailUrl());
   assertEquals(1200, result.photoWidth());
   assertEquals(800, result.photoHeight());
  }

  @Test
  void paginatesRecipesAndFiltersTheirCookingHistory() {
   Recipes recipes = mock(Recipes.class); RecipePhotos photos = mock(RecipePhotos.class); Cookings cookings = mock(Cookings.class); CookingReviews reviews = mock(CookingReviews.class);
   User tomas = user(7L, "tomas");
   Recipe best = recipe(1L, "Pastas", tomas, "2026-07-23T00:00:00Z");
   Recipe other = recipe(2L, "Pizza", tomas, "2026-07-22T00:00:00Z");
   Recipe pending = recipe(3L, "Pan", tomas, "2026-07-21T00:00:00Z");
   when(recipes.findAll()).thenReturn(List.of(best, other, pending));
   when(cookings.cookingCountsByRecipeIdIn(any())).thenReturn(List.of(count(1L, 2L), count(2L, 1L)));
   when(cookings.homesByRecipeIdIn(any())).thenReturn(List.of(home(1L, Home.TOMAS), home(1L, Home.AVRIL), home(2L, Home.TOMAS)));
   when(reviews.ratingsByRecipeIdIn(any())).thenReturn(List.of(rating(1L, 5.0), rating(2L, 3.0)));

   HomeRecipeApi api = new HomeRecipeApi(recipes, photos, cookings, reviews, null);
   Slice<RecipeDto> first = api.listRecipes(null, Home.TOMAS, true, "rating-desc", null, 1);
   Slice<RecipeDto> second = api.listRecipes(null, Home.TOMAS, true, "rating-desc", first.nextCursor(), 1);
   Slice<RecipeDto> uncooked = api.listRecipes("pan", null, false, "date-desc", null, 5);

   assertEquals(List.of(1L), first.content().stream().map(RecipeDto::id).toList());
   assertEquals(1L, first.nextCursor());
   assertEquals(2L, first.content().getFirst().cookingCount());
   assertEquals(List.of(Home.TOMAS, Home.AVRIL), first.content().getFirst().homes());
   assertEquals(5.0, first.content().getFirst().rating());
   assertEquals(List.of(2L), second.content().stream().map(RecipeDto::id).toList());
   assertEquals(null, second.nextCursor());
   assertEquals(List.of(3L), uncooked.content().stream().map(RecipeDto::id).toList());
  }

  private static User user(Long id, String username) { User user = new User(); user.id = id; user.username = username; return user; }
  private static Recipe recipe(Long id, String name, User author, String updatedAt) { Recipe recipe = new Recipe(); recipe.id = id; recipe.name = name; recipe.createdBy = recipe.updatedBy = author; recipe.createdAt = recipe.updatedAt = Instant.parse(updatedAt); return recipe; }
  private static RecipePhotoMetadata photo(Long id, Long recipeId, Integer width, Integer height) { return new RecipePhotoMetadata() { public Long getId() { return id; } public Long getRecipeId() { return recipeId; } public Integer getWidth() { return width; } public Integer getHeight() { return height; } public Instant getCreatedAt() { return Instant.parse("2026-07-23T00:00:00Z"); } }; }
  private static RecipeCookingCount count(Long recipeId, Long cookingCount) { return new RecipeCookingCount() { public Long getRecipeId() { return recipeId; } public Long getCookingCount() { return cookingCount; } }; }
  private static RecipeHome home(Long recipeId, Home home) { return new RecipeHome() { public Long getRecipeId() { return recipeId; } public Home getHome() { return home; } }; }
  private static RecipeRating rating(Long recipeId, Double rating) { return new RecipeRating() { public Long getRecipeId() { return recipeId; } public Double getRating() { return rating; } }; }
}
