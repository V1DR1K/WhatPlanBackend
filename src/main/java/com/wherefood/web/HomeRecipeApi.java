package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.application.RecipeInput;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.wherefood.validation.SafeHttpUrl;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.config.CoupleContext;

record RecipeIngredientRequest(@NotBlank @Size(max = 160) String name, @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity, @NotBlank @Size(max = 30) String unit) {}
record RecipeStepRequest(@NotBlank @Size(max = 2000) String instruction) {}
record RecipeRequest(@NotBlank @Size(max = 160) String name, @Size(max = 1000) @SafeHttpUrl String sourceUrl, @Size(max = 50) List<@Valid RecipeIngredientRequest> ingredients, @Size(max = 50) List<@Valid RecipeStepRequest> steps, @Positive Long zoneId, UUID stageId) {
 RecipeInput toInput() {
  List<RecipeInput.Ingredient> recipeIngredients = ingredients == null ? null : ingredients.stream()
    .map(value -> new RecipeInput.Ingredient(value.name(), value.quantity(), value.unit())).toList();
  List<RecipeInput.Step> recipeSteps = steps == null ? null : steps.stream()
    .map(value -> new RecipeInput.Step(value.instruction())).toList();
  return new RecipeInput(name, sourceUrl, recipeIngredients, recipeSteps, zoneId, stageId);
 }
 RecipeRequest(String name, String sourceUrl, List<RecipeIngredientRequest> ingredients, List<RecipeStepRequest> steps) {
  this(name, sourceUrl, ingredients, steps, null, null);
 }
 RecipeRequest(String name, String sourceUrl, List<RecipeIngredientRequest> ingredients, List<RecipeStepRequest> steps, Long zoneId) { this(name, sourceUrl, ingredients, steps, zoneId, null); }
}
record CookingRequest(@NotNull Home home, @Min(1) @Max(100) int servings, @NotNull LocalDate cookedOn, @NotNull MealType mealType, @Positive Long cityId, UUID stageId, UUID pointId) {
 CookingRequest(Home home, int servings, LocalDate cookedOn, MealType mealType) { this(home, servings, cookedOn, mealType,null,null,null); }
}
record RecipeIngredientDto(String name, BigDecimal quantity, String unit) {}
record RecipeStepDto(String instruction) {}
record RecipeDto(Long id, Long zoneId, String name, String sourceUrl, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, Double rating, Double complexityRating, Double tasteRating, long cookingCount, List<Home> homes, List<RecipeIngredientDto> ingredients, List<RecipeStepDto> steps, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {}
record CookingReviewRequest(@Min(1) @Max(5) short rating, @Min(1) @Max(5) short complexity, @Min(1) @Max(5) short taste, @Size(max = 1000) String comment) {}
record CookingReviewDto(Long id, String author, String updatedBy, short rating, short complexity, short taste, String comment, Instant createdAt, Instant updatedAt) {}
record CookingDto(Long id, RecipeDto recipe, Home home, int servings, LocalDate cookedOn, MealType mealType, String createdBy, String updatedBy, List<CookingReviewDto> reviews, Instant createdAt, Instant updatedAt, Long cityId, UUID stageId) {}

/**
 * Active WhoCook contract: recipes hold reusable definitions; cookings are
 * dated executions at a home. Only reusable recipes own profile media.
 */
@RestController
@RequestMapping("/api/how-cook")
public class HomeRecipeApi {
 private final Recipes recipes;
 private final RecipePhotos recipePhotos;
 private final Cookings cookings;
 private final CookingReviews reviews;
 private final PhotoStorage storage;
 private final CookingReviewService reviewService;
 private final RecipeService recipeService;
 private final CookingService cookingService;
 private final RecipeMediaService mediaService;

 public HomeRecipeApi(Recipes recipes, RecipePhotos recipePhotos, Cookings cookings, CookingReviews reviews, PhotoStorage storage) {
 this(recipes, recipePhotos, cookings, reviews, storage,
          new CookingReviewService(reviews, cookings, new CoupleAuthorizationService(null)),
          new RecipeService(recipes, cookings, new CoupleAuthorizationService(null)),
          new CookingService(recipes, cookings, new CoupleAuthorizationService(null)));
 }

 public HomeRecipeApi(Recipes recipes, RecipePhotos recipePhotos, Cookings cookings, CookingReviews reviews, PhotoStorage storage, CookingReviewService reviewService) {
  this(recipes, recipePhotos, cookings, reviews, storage, reviewService,
          new RecipeService(recipes, cookings, new CoupleAuthorizationService(null)),
          new CookingService(recipes, cookings, new CoupleAuthorizationService(null)));
 }

 public HomeRecipeApi(Recipes recipes, RecipePhotos recipePhotos, Cookings cookings, CookingReviews reviews,
         PhotoStorage storage, CookingReviewService reviewService, RecipeService recipeService,
         CookingService cookingService) {
  this(recipes, recipePhotos, cookings, reviews, storage, reviewService, recipeService, cookingService,
          new RecipeMediaService(recipes, recipePhotos, storage, new CoupleAuthorizationService(null)));
 }

 @org.springframework.beans.factory.annotation.Autowired
 public HomeRecipeApi(Recipes recipes, RecipePhotos recipePhotos, Cookings cookings, CookingReviews reviews,
         PhotoStorage storage, CookingReviewService reviewService, RecipeService recipeService,
         CookingService cookingService, RecipeMediaService mediaService) {
  this.recipes = recipes; this.recipePhotos = recipePhotos; this.cookings = cookings; this.reviews = reviews;
  this.storage = storage; this.reviewService = reviewService; this.recipeService = recipeService;
  this.cookingService = cookingService; this.mediaService = mediaService;
 }

  @GetMapping("/recipes") @Transactional(readOnly = true) Slice<RecipeDto> listRecipes(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) String search, @RequestParam(required = false) Home home, @RequestParam(required = false) Boolean cooked, @RequestParam(required = false) String sort, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int size) {
   int limit = Math.max(1, Math.min(size, 30));
   long offset = cursor == null ? 0 : Math.max(0, cursor);
   if (offset > 1_000_000) throw badRequest("Cursor inválido");
   String normalizedSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
   String normalizedSort = sort == null ? "date-desc" : sort.trim().toLowerCase(Locale.ROOT);
   if (!Set.of("date", "date-desc", "date-asc", "rating", "rating-desc", "rating-asc").contains(normalizedSort)) {
    throw badRequest("Orden inválido");
   }
   List<Long> ids = recipes.findPageIdsByCoupleId(CoupleContext.current(), normalizedSearch,
           home == null ? null : home.name(), cooked, normalizedSort, limit + 1, offset);
   Long next = ids.size() > limit ? offset + limit : null;
   List<Long> pageIds = ids.stream().limit(limit).toList();
   if (pageIds.isEmpty()) return new Slice<>(List.of(), next);
   Map<Long, Recipe> byId = recipes.findAllByIdInAndCoupleId(pageIds, CoupleContext.current()).stream()
           .collect(java.util.stream.Collectors.toMap(recipe -> recipe.id, recipe -> recipe));
   List<Recipe> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
   Map<Long, RecipeSummary> summaries = recipeSummaries(pageIds);
   Map<Long, RecipePhotoMetadata> photosByRecipe = recipePhotos(page);
   return new Slice<>(page.stream().map(recipe -> recipe(recipe, summaries.get(recipe.id), photosByRecipe.get(recipe.id))).toList(), next);
  }
 Slice<RecipeDto> listRecipes(String search, Home home, Boolean cooked, String sort, Long cursor, int size) {
  return listRecipes(null, search, home, cooked, sort, cursor, size);
 }
 @GetMapping("/recipes/{id}") @Transactional(readOnly = true) RecipeDto getRecipe(@PathVariable Long id) { return recipe(findRecipe(id)); }
 @PostMapping("/recipes") @ResponseStatus(HttpStatus.CREATED) RecipeDto addRecipe(@RequestBody @Valid RecipeRequest request, @AuthenticationPrincipal User author) {
  return recipe(recipeService.create(request.toInput(), author));
 }
 @PutMapping("/recipes/{id}") RecipeDto updateRecipe(@PathVariable Long id, @RequestBody @Valid RecipeRequest request, @AuthenticationPrincipal User author) {
  return recipe(recipeService.update(id, request.toInput(), author));
 }
  @DeleteMapping("/recipes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteRecipe(@PathVariable Long id, @AuthenticationPrincipal User author) {
   recipeService.delete(id, author);
  }
  @GetMapping(value = "/recipes/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> recipePhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   findRecipe(id); RecipePhoto photo = recipePhotos.findByRecipeIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
   return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping(value = "/recipes/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) RecipeDto uploadRecipePhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   return recipe(mediaService.replacePhoto(id, file, author));
  }

 @GetMapping("/cookings") @Transactional(readOnly = true) Slice<CookingDto> listCookings(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) Home home, @RequestParam(required = false) Long recipeId,
         @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "10") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  long offset = cursor == null ? 0 : Math.max(0, cursor);
  if (offset > 1_000_000) throw badRequest("Cursor inválido");
  UUID coupleId = CoupleContext.current();
  List<Long> ids = cookings.findPageIdsByCoupleId(coupleId, recipeId,
          home == null ? null : home.name(), limit + 1, offset);
  Long next = ids.size() > limit ? offset + limit : null;
  List<Long> pageIds = ids.stream().limit(limit).toList();
  if (pageIds.isEmpty()) return new Slice<>(List.of(), null);
  Map<Long, Cooking> byId = cookings.findAllByIdInAndCoupleId(pageIds, coupleId).stream()
          .collect(java.util.stream.Collectors.toMap(value -> value.id, value -> value));
  List<CookingDto> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).map(this::cooking).toList();
  return new Slice<>(page, next);
 }
 Slice<CookingDto> listCookings(Home home, Long recipeId, Long cursor, int size) {
  return listCookings(null, home, recipeId, cursor, size);
 }
  @PostMapping("/recipes/{recipeId}/cookings") @ResponseStatus(HttpStatus.CREATED) CookingDto addCooking(@PathVariable Long recipeId, @RequestBody @Valid CookingRequest request, @AuthenticationPrincipal User author) {
   return cooking(cookingService.create(recipeId, request, author));
 }
 @GetMapping("/cookings/{id}") @Transactional(readOnly = true) CookingDto getCooking(@PathVariable Long id) { return cooking(findCooking(id)); }
 @PutMapping("/cookings/{id}") CookingDto updateCooking(@PathVariable Long id, @RequestBody @Valid CookingRequest request, @AuthenticationPrincipal User author) {
   return cooking(cookingService.update(id, request, author));
 }
  @DeleteMapping("/cookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCooking(@PathVariable Long id, @AuthenticationPrincipal User author) { cookingService.delete(id, author); }

 @PostMapping("/cookings/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) CookingReviewDto addReview(@PathVariable Long id, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.create(id, request, author));
 }
 @PutMapping("/cookings/{id}/reviews/me") CookingReviewDto saveOwnReview(@PathVariable Long id, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.saveOwn(id, request, author));
 }
 @PutMapping("/cooking-reviews/{reviewId}") CookingReviewDto updateReview(@PathVariable Long reviewId, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.update(reviewId, request, author));
 }
 @DeleteMapping("/cooking-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long reviewId, @AuthenticationPrincipal User author) { reviewService.delete(reviewId, author); }

 private Recipe findRecipe(Long id) { return recipes.findByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Receta")); }
 private Cooking findCooking(Long id) { return cookings.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Preparación")); }
  private CookingDto cooking(Cooking value) {
  List<CookingReview> reviewValues = reviews.findByCookingIdAndCoupleIdOrderByAuthorUsername(value.id, CoupleContext.current());
  Map<Long, String> reviewAuthors = reviews.authorsByCookingIdAndCoupleId(value.id, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
  return new CookingDto(value.id, recipe(value.recipe), value.home, value.servings, value.cookedOn, value.mealType, value.createdBy.username, value.updatedBy.username, reviewValues.stream().map(review -> review(review, reviewAuthors.get(review.id))).toList(), value.createdAt, value.updatedAt, value.cityId, value.stageId);
 }
  private RecipeDto recipe(Recipe value) {
   return recipe(value, recipeSummaries(List.of(value.id)).get(value.id), recipePhotos(List.of(value)).get(value.id));
  }
  private RecipeDto recipe(Recipe value, RecipeSummary summary, PhotoMetadata photo) {
   return new RecipeDto(value.id, value.zoneId, value.name, value.sourceUrl, photo == null ? null : recipePhotoUrl(value.id, false, photo.getId()), photo == null ? null : recipePhotoUrl(value.id, true, photo.getId()), photo == null ? null : photo.getWidth(), photo == null ? null : photo.getHeight(), summary.rating(), summary.complexityRating(), summary.tasteRating(), summary.cookingCount(), summary.homes(), value.ingredients.stream().map(ingredient -> new RecipeIngredientDto(ingredient.name, ingredient.quantity, ingredient.unit)).toList(), value.steps.stream().map(step -> new RecipeStepDto(step.instruction)).toList(), value.createdBy.username, value.updatedBy.username, value.createdAt, value.updatedAt);
  }
  private Map<Long, RecipeSummary> recipeSummaries(Collection<Long> recipeIds) {
   if (recipeIds.isEmpty()) return Map.of();
   Map<Long, Long> counts = cookings == null ? Map.of() : cookings.cookingCountsByRecipeIdInAndCoupleId(recipeIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(RecipeCookingCount::getRecipeId, RecipeCookingCount::getCookingCount));
   Map<Long, EnumSet<Home>> homes = new HashMap<>();
   if (cookings != null) for (RecipeHome value : cookings.homesByRecipeIdInAndCoupleId(recipeIds, CoupleContext.current())) homes.computeIfAbsent(value.getRecipeId(), ignored -> EnumSet.noneOf(Home.class)).add(value.getHome());
   Map<Long, RecipeRating> ratings = reviews == null ? Map.of() : reviews.ratingsByRecipeIdInAndCoupleId(recipeIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(RecipeRating::getRecipeId, value -> value));
   Map<Long, RecipeSummary> result = new HashMap<>();
   for (Long recipeId : recipeIds) {
    RecipeRating rating = ratings.get(recipeId);
    result.put(recipeId, new RecipeSummary(counts.getOrDefault(recipeId, 0L), homes.containsKey(recipeId) ? List.copyOf(homes.get(recipeId)) : List.of(), rating == null ? null : rating.getRating(), rating == null ? null : rating.getComplexityRating(), rating == null ? null : rating.getTasteRating()));
   }
   return result;
  }
  private Map<Long, RecipePhotoMetadata> recipePhotos(Collection<Recipe> values) {
   if (values.isEmpty() || recipePhotos == null) return Map.of();
   return recipePhotos.metadataByRecipeIdInAndCoupleId(values.stream().map(recipe -> recipe.id).toList(), CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(RecipePhotoMetadata::getRecipeId, photo -> photo));
  }
  private record RecipeSummary(long cookingCount, List<Home> homes, Double rating, Double complexityRating, Double tasteRating) {}
 private static String recipePhotoUrl(Long recipeId, boolean thumbnail, Long photoId) { return "/how-cook/recipes/" + recipeId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
 private static CookingReviewDto review(CookingReview value) { return review(value, value.author.username); }
  private static CookingReviewDto review(CookingReview value, String author) { return new CookingReviewDto(value.id, author, value.updatedBy.username, value.rating, value.complexity, value.taste, value.comment, value.createdAt, value.updatedAt); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
