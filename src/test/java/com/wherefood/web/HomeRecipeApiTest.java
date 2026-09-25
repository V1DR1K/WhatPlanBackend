package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.*;
import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.OrderColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HomeRecipeApiTest {
 @AfterEach
 void clearCoupleContext() { CoupleContext.clear(); }

 @Test
 void letsEitherActiveMemberUpdateACooking() {
   Recipes recipes = mock(Recipes.class); Cookings cookings = mock(Cookings.class); CookingReviews reviews = mock(CookingReviews.class);
  User tomas = user(7L, "tomas"), avril = user(6L, "avril"); Recipe recipe = new Recipe(); recipe.id = 3L; recipe.name = "Panes rellenos"; recipe.createdBy = recipe.updatedBy = tomas;
  Cooking cooking = new Cooking(); cooking.id = 2L; cooking.recipe = recipe; cooking.home = Home.TOMAS; cooking.servings = 2; cooking.cookedOn = LocalDate.of(2026, 7, 18); cooking.mealType = MealType.CENA; cooking.createdBy = cooking.updatedBy = tomas;
   UUID coupleId = UUID.randomUUID();
   when(cookings.findDetailedByIdAndCoupleId(2L, coupleId)).thenReturn(Optional.of(cooking)); when(cookings.save(cooking)).thenReturn(cooking); when(reviews.findByCookingIdAndCoupleIdOrderByAuthorUsername(2L, coupleId)).thenReturn(List.of());

   CookingDto result = authorizedApi(recipes, mock(RecipePhotos.class), cookings, reviews, null, avril, coupleId).updateCooking(2L, new CookingRequest(Home.AVRIL, 4, LocalDate.of(2026, 7, 21), MealType.ALMUERZO), avril);

   assertEquals(Home.AVRIL, result.home()); assertEquals("avril", result.updatedBy()); verify(cookings).save(cooking); verify(recipes).save(recipe);
 }

 @Test
  void createsAReusableRecipeDefinition() {
  Recipes recipes = mock(Recipes.class); User tomas = user(7L, "tomas"); when(recipes.save(any(Recipe.class))).thenAnswer(invocation -> { Recipe value = invocation.getArgument(0); value.id = 5L; return value; });

   RecipeDto result = authorizedApi(recipes, mock(RecipePhotos.class), null, null, null, tomas, UUID.randomUUID()).addRecipe(new RecipeRequest("Tarta", "https://example.test/tarta", List.of(new RecipeIngredientRequest("Harina", BigDecimal.valueOf(250), "g")), List.of(new RecipeStepRequest("Hornear."))), tomas);

  assertEquals(5L, result.id()); assertEquals("Tarta", result.name()); assertEquals(1, result.ingredients().size()); assertEquals("tomas", result.createdBy());
 }

  @Test
  void createsARecipeWithoutIngredientsOrSteps() {
   Recipes recipes = mock(Recipes.class); User tomas = user(7L, "tomas"); when(recipes.save(any(Recipe.class))).thenAnswer(invocation -> { Recipe value = invocation.getArgument(0); value.id = 5L; return value; });

   RecipeDto result = authorizedApi(recipes, mock(RecipePhotos.class), null, null, null, tomas, UUID.randomUUID()).addRecipe(new RecipeRequest("Tarta", null, List.of(), List.of()), tomas);

   assertEquals(List.of(), result.ingredients()); assertEquals(List.of(), result.steps());
  }

 @Test
 void listsRecipesWithIngredientsAndStepsUsingIndexedCollections() throws NoSuchFieldException {
  Recipes recipes = mock(Recipes.class); User tomas = user(7L, "tomas"); Recipe recipe = new Recipe(); recipe.id = 5L; recipe.name = "Tarta"; recipe.createdBy = recipe.updatedBy = tomas; recipe.updatedAt = Instant.parse("2026-07-23T00:00:00Z");
  RecipeIngredient ingredient = new RecipeIngredient(); ingredient.name = "Harina"; ingredient.quantity = BigDecimal.valueOf(250); ingredient.unit = "g"; ingredient.position = 0; recipe.ingredients.add(ingredient);
  RecipeStep step = new RecipeStep(); step.instruction = "Hornear."; step.position = 0; recipe.steps.add(step);
  when(recipes.findPageIdsByCoupleId(null, null, null, null, "date-desc", 6, 0)).thenReturn(List.of(5L));
  when(recipes.findAllByIdInAndCoupleId(List.of(5L), null)).thenReturn(List.of(recipe));

   Slice<RecipeDto> result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), null, null, null).listRecipes(null, null, null, null, null, 5);

   assertEquals("Harina", result.content().getFirst().ingredients().getFirst().name()); assertEquals("Hornear.", result.content().getFirst().steps().getFirst().instruction());
  assertEquals("position", Recipe.class.getDeclaredField("ingredients").getAnnotation(OrderColumn.class).name());
   assertEquals("position", Recipe.class.getDeclaredField("steps").getAnnotation(OrderColumn.class).name());
  }

  @Test
  void updatesCookingReviewComplexity() {
   CookingReviews reviews = mock(CookingReviews.class); User tomas = user(7L, "tomas"); CookingReview review = new CookingReview(); review.id = 8L; review.author = review.updatedBy = tomas;
   CoupleMembers members = mock(CoupleMembers.class); UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId); when(members.findActiveCoupleIdByUserId(tomas.id)).thenReturn(Optional.of(coupleId));
   when(reviews.findDetailedByIdAndCoupleId(8L, coupleId)).thenReturn(Optional.of(review)); when(reviews.save(review)).thenReturn(review);

    CookingReviewDto result = new HomeRecipeApi(null, null, null, reviews, null, new CookingReviewService(reviews, null, new CoupleAuthorizationService(members))).updateReview(8L, new CookingReviewRequest((short) 4, (short) 2, (short) 5, "Quedó bien\n\nLa repetiría\n"), tomas);

    assertEquals(4, result.rating()); assertEquals(2, result.complexity()); assertEquals(5, result.taste()); assertEquals("Quedó bien\n\nLa repetiría\n", result.comment());
  }

  @Test
  void projectsTheRecipeProfileSeparatelyFromCookings() {
   Recipes recipes = mock(Recipes.class); RecipePhotos profilePhotos = mock(RecipePhotos.class); User tomas = user(7L, "tomas");
   Recipe recipe = new Recipe(); recipe.id = 5L; recipe.name = "Tarta"; recipe.createdBy = recipe.updatedBy = tomas; recipe.updatedAt = Instant.parse("2026-07-23T00:00:00Z");
   when(recipes.findPageIdsByCoupleId(null, null, null, null, "date-desc", 6, 0)).thenReturn(List.of(5L));
   when(recipes.findAllByIdInAndCoupleId(List.of(5L), null)).thenReturn(List.of(recipe)); when(profilePhotos.metadataByRecipeIdInAndCoupleId(any(), isNull())).thenReturn(List.of(photo(12L, 5L, 1200, 800)));

   RecipeDto result = new HomeRecipeApi(recipes, profilePhotos, null, null, null).listRecipes(null, null, null, null, null, 5).content().getFirst();

   assertEquals("/how-cook/recipes/5/photo?v=12", result.photoUrl());
   assertEquals("/how-cook/recipes/5/photo?thumbnail=true&v=12", result.thumbnailUrl());
  assertEquals(1200, result.photoWidth());
  assertEquals(800, result.photoHeight());
  }

  @Test
  void paginatesUnfilteredRecipesInDatabaseWithinCurrentCouple() {
   Recipes recipes = mock(Recipes.class);
   Recipe recipe = recipe(8L, "Guiso", user(7L, "tomas"), "2026-07-23T00:00:00Z");
   UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
   when(recipes.findPageIdsByCoupleId(coupleId, null, null, null, "date-desc", 2, 30)).thenReturn(List.of(8L, 9L));
   when(recipes.findAllByIdInAndCoupleId(List.of(8L), coupleId)).thenReturn(List.of(recipe));

   Slice<RecipeDto> result = new HomeRecipeApi(recipes, mock(RecipePhotos.class), null, null, null)
           .listRecipes(null, null, null, null, 30L, 1);

   assertEquals(List.of(8L), result.content().stream().map(RecipeDto::id).toList());
   assertEquals(31L, result.nextCursor());
   verify(recipes).findPageIdsByCoupleId(coupleId, null, null, null, "date-desc", 2, 30);
   verify(recipes).findAllByIdInAndCoupleId(List.of(8L), coupleId);
  }

  @Test
  void paginatesRecipesAndFiltersTheirCookingHistory() {
   Recipes recipes = mock(Recipes.class); RecipePhotos photos = mock(RecipePhotos.class); Cookings cookings = mock(Cookings.class); CookingReviews reviews = mock(CookingReviews.class);
   User tomas = user(7L, "tomas");
   Recipe best = recipe(1L, "Pastas", tomas, "2026-07-23T00:00:00Z");
   Recipe other = recipe(2L, "Pizza", tomas, "2026-07-22T00:00:00Z");
   Recipe pending = recipe(3L, "Pan", tomas, "2026-07-21T00:00:00Z");
   when(recipes.findPageIdsByCoupleId(null, null, "TOMAS", true, "rating-desc", 2, 0)).thenReturn(List.of(1L, 2L));
   when(recipes.findPageIdsByCoupleId(null, null, "TOMAS", true, "rating-desc", 2, 1)).thenReturn(List.of(2L));
   when(recipes.findPageIdsByCoupleId(null, "pan", null, false, "date-desc", 6, 0)).thenReturn(List.of(3L));
   when(recipes.findAllByIdInAndCoupleId(List.of(1L), null)).thenReturn(List.of(best));
   when(recipes.findAllByIdInAndCoupleId(List.of(2L), null)).thenReturn(List.of(other));
   when(recipes.findAllByIdInAndCoupleId(List.of(3L), null)).thenReturn(List.of(pending));
   when(cookings.cookingCountsByRecipeIdInAndCoupleId(any(), isNull())).thenReturn(List.of(count(1L, 2L), count(2L, 1L)));
   when(cookings.homesByRecipeIdInAndCoupleId(any(), isNull())).thenReturn(List.of(home(1L, Home.TOMAS), home(1L, Home.AVRIL), home(2L, Home.TOMAS)));
   when(reviews.ratingsByRecipeIdInAndCoupleId(any(), isNull())).thenReturn(List.of(rating(1L, 5.0), rating(2L, 3.0)));

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
   verify(recipes).findPageIdsByCoupleId(null, null, "TOMAS", true, "rating-desc", 2, 0);
   verify(recipes).findPageIdsByCoupleId(null, "pan", null, false, "date-desc", 6, 0);
  }

  @Test
  void paginatesCookingHistoryWithTheActiveCoupleAndStableCursor() {
   Cookings cookings = mock(Cookings.class);
   UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
   when(cookings.findPageIdsByCoupleId(coupleId, 42L, null, 3, 0)).thenReturn(List.of(9L, 8L, 7L));

   Slice<CookingDto> result = new HomeRecipeApi(null, null, cookings, null, null)
           .listCookings(null, 42L, null, 2);

   assertEquals(List.of(), result.content());
   assertEquals(2L, result.nextCursor());
   verify(cookings).findPageIdsByCoupleId(coupleId, 42L, null, 3, 0);
   verify(cookings).findAllByIdInAndCoupleId(List.of(9L, 8L), coupleId);
  }

  private static User user(Long id, String username) { User user = new User(); user.id = id; user.username = username; user.role = Role.USER; return user; }
  private static HomeRecipeApi authorizedApi(Recipes recipes, RecipePhotos photos, Cookings cookings,
          CookingReviews reviews, PhotoStorage storage, User user, UUID coupleId) {
   CoupleContext.set(coupleId);
   CoupleMembers members = mock(CoupleMembers.class);
   when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(coupleId));
   CoupleAuthorizationService authorization = new CoupleAuthorizationService(members);
   return new HomeRecipeApi(recipes, photos, cookings, reviews, storage,
           new CookingReviewService(reviews, cookings, authorization),
           new RecipeService(recipes, cookings, authorization),
           new CookingService(recipes, cookings, authorization));
  }
  private static Recipe recipe(Long id, String name, User author, String updatedAt) { Recipe recipe = new Recipe(); recipe.id = id; recipe.name = name; recipe.createdBy = recipe.updatedBy = author; recipe.createdAt = recipe.updatedAt = Instant.parse(updatedAt); return recipe; }
  private static RecipePhotoMetadata photo(Long id, Long recipeId, Integer width, Integer height) { return new RecipePhotoMetadata() { public Long getId() { return id; } public Long getRecipeId() { return recipeId; } public Integer getWidth() { return width; } public Integer getHeight() { return height; } public Instant getCreatedAt() { return Instant.parse("2026-07-23T00:00:00Z"); } }; }
  private static RecipeCookingCount count(Long recipeId, Long cookingCount) { return new RecipeCookingCount() { public Long getRecipeId() { return recipeId; } public Long getCookingCount() { return cookingCount; } }; }
  private static RecipeHome home(Long recipeId, Home home) { return new RecipeHome() { public Long getRecipeId() { return recipeId; } public Home getHome() { return home; } }; }
  private static RecipeRating rating(Long recipeId, Double rating) { return new RecipeRating() { public Long getRecipeId() { return recipeId; } public Double getRating() { return rating; } public Double getComplexityRating() { return rating; } public Double getTasteRating() { return rating; } }; }
}
