package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record HomeRecipeIngredientRequest(@NotBlank @Size(max = 160) String name, @DecimalMin(value = "0.0", inclusive = false) java.math.BigDecimal quantity, @NotBlank @Size(max = 30) String unit) {}
record HomeRecipeStepRequest(@NotBlank @Size(max = 2000) String instruction) {}
record HomeRecipeRequest(@NotNull Home home, @NotBlank @Size(max = 160) String name, @Min(1) @Max(100) int servings, @Size(max = 1000) String recipeUrl, @NotNull LocalDate preparedOn, @NotNull MealType mealType, @NotEmpty List<@Valid HomeRecipeIngredientRequest> ingredients, @NotEmpty List<@Valid HomeRecipeStepRequest> steps, Long repeatedFromId) {}
record HomeRecipeReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record HomeRecipeIngredientDto(String name, java.math.BigDecimal quantity, String unit) {}
record HomeRecipeStepDto(String instruction) {}
record HomeRecipeReferenceDto(Long id, String name) {}
record HomeRecipeReviewDto(Long id, String author, short rating, String comment, Instant updatedAt) {}
record HomeRecipeDto(Long id, Home home, String name, int servings, String recipeUrl, LocalDate preparedOn, MealType mealType, List<HomeRecipeIngredientDto> ingredients, List<HomeRecipeStepDto> steps, HomeRecipeReferenceDto repeatedFrom, List<HomeRecipeReferenceDto> repetitions, String author, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<HomeRecipeReviewDto> reviews, Instant createdAt) {}

@RestController
@RequestMapping("/api/how-cook")
public class HomeRecipeApi {
 private final HomeRecipes recipes;
 private final HomeRecipePhotos photos;
 private final HomeRecipeReviews reviews;
 private final PhotoStorage storage;

 public HomeRecipeApi(HomeRecipes recipes, HomeRecipePhotos photos, HomeRecipeReviews reviews, PhotoStorage storage) {
  this.recipes = recipes; this.photos = photos; this.reviews = reviews; this.storage = storage;
 }

  @GetMapping List<HomeRecipeDto> list(@RequestParam Home home, @RequestParam(required = false) String search) {
   List<HomeRecipe> values = recipes.findByHomeOrderByPreparedOnDescIdDesc(home).stream().filter(recipe -> search == null || search.isBlank() || recipe.name.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))).toList();
  Map<Long, HomeRecipePhoto> photoMap = photoMap(values);
  Map<Long, List<HomeRecipeReviewDto>> reviewMap = reviewMap(values);
   return values.stream().map(recipe -> recipe(recipe, photoMap.get(recipe.id), reviewMap.getOrDefault(recipe.id, List.of()), List.of())).toList();
 }

 @GetMapping("/{id}") HomeRecipeDto get(@PathVariable Long id) {
  HomeRecipe recipe = findRecipe(id);
   return recipe(recipe, photos.findByRecipeId(id).orElse(null), reviewMap(List.of(recipe)).getOrDefault(id, List.of()), recipes.findByRepeatedFromIdOrderByPreparedOnDescIdDesc(id).stream().map(HomeRecipeApi::reference).toList());
 }

 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional HomeRecipeDto add(@RequestBody @Valid HomeRecipeRequest request, @AuthenticationPrincipal User author) {
   HomeRecipe recipe = new HomeRecipe(); recipe.author = author; apply(recipe, request); recipe.createdAt = recipe.updatedAt = Instant.now();
   HomeRecipe saved = recipes.save(recipe); HomeRecipePhoto photo = request.repeatedFromId() == null ? null : photos.findByRecipeId(request.repeatedFromId()).map(source -> copyPhoto(saved, source)).orElse(null);
   return recipe(saved, photo, List.of(), List.of());
 }

  @PutMapping("/{id}") HomeRecipeDto update(@PathVariable Long id, @RequestBody @Valid HomeRecipeRequest request, @AuthenticationPrincipal User author) {
   HomeRecipe recipe = owned(findRecipe(id), author); apply(recipe, request); recipe.updatedAt = Instant.now();
   return recipe(recipes.save(recipe), photos.findByRecipeId(id).orElse(null), reviewMap(List.of(recipe)).getOrDefault(id, List.of()), recipes.findByRepeatedFromIdOrderByPreparedOnDescIdDesc(id).stream().map(HomeRecipeApi::reference).toList());
  }

  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id, @AuthenticationPrincipal User author) {
   HomeRecipe recipe = owned(findRecipe(id), author); if (recipes.existsByRepeatedFromId(id)) throw conflict("No podés borrar una receta que tiene preparaciones repetidas"); recipes.delete(recipe);
 }

  @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional HomeRecipeDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   HomeRecipe recipe = owned(findRecipe(id), author); photos.findByRecipeId(id).ifPresent(photos::delete); photos.flush(); HomeRecipePhoto photo = photos.save(storage.store(recipe, file));
   return recipe(recipe, photo, reviewMap(List.of(recipe)).getOrDefault(id, List.of()), recipes.findByRepeatedFromIdOrderByPreparedOnDescIdDesc(id).stream().map(HomeRecipeApi::reference).toList());
 }

 @PutMapping("/{id}/reviews/me") @Transactional HomeRecipeReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid HomeRecipeReviewRequest request, @AuthenticationPrincipal User author) {
  HomeRecipe recipe = findRecipe(id);
  HomeRecipeReview review = reviews.findByRecipeIdAndAuthorId(id, author.id).orElseGet(() -> { HomeRecipeReview value = new HomeRecipeReview(); value.recipe = recipe; value.author = author; value.createdAt = Instant.now(); return value; });
  review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.updatedAt = Instant.now();
  return review(reviews.save(review));
 }

 @PutMapping("/{recipeId}/reviews/{reviewId}") @Transactional HomeRecipeReviewDto updateReview(@PathVariable Long recipeId, @PathVariable Long reviewId, @RequestBody @Valid HomeRecipeReviewRequest request, @AuthenticationPrincipal User author) {
  HomeRecipeReview review = reviews.findById(reviewId).filter(value -> value.recipe.id.equals(recipeId)).orElseThrow(() -> notFound("Reseña"));
  if (author.role != Role.ADMIN && !review.author.id.equals(author.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.updatedAt = Instant.now();
  return review(reviews.save(review));
 }

 @GetMapping(value = "/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
  HomeRecipePhoto photo = photos.findByRecipeId(id).orElseThrow(() -> notFound("Foto"));
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 private HomeRecipe findRecipe(Long id) { return recipes.findById(id).orElseThrow(() -> notFound("Receta")); }
 private Map<Long, HomeRecipePhoto> photoMap(List<HomeRecipe> values) {
  if (values.isEmpty()) return Map.of();
  return photos.findByRecipeIdIn(values.stream().map(recipe -> recipe.id).toList()).stream().collect(java.util.stream.Collectors.toMap(photo -> photo.recipe.id, photo -> photo));
 }
 private Map<Long, List<HomeRecipeReviewDto>> reviewMap(List<HomeRecipe> values) {
  if (values.isEmpty()) return Map.of();
  return reviews.findByRecipeIdInOrderByAuthorUsername(values.stream().map(recipe -> recipe.id).toList()).stream().collect(java.util.stream.Collectors.groupingBy(review -> review.recipe.id, java.util.stream.Collectors.mapping(HomeRecipeApi::review, java.util.stream.Collectors.toList())));
 }
  private void apply(HomeRecipe recipe, HomeRecipeRequest request) {
   if (request.preparedOn().isAfter(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una receta preparada no puede quedar en el futuro");
   HomeRecipe repeatedFrom = request.repeatedFromId() == null ? null : findRecipe(request.repeatedFromId());
   if (repeatedFrom != null && recipe.id != null && repeatedFrom.id.equals(recipe.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una receta no puede repetirse a sí misma");
   recipe.home = request.home(); recipe.name = request.name().trim(); recipe.servings = request.servings(); recipe.recipeUrl = blankToNull(request.recipeUrl()); recipe.preparedOn = request.preparedOn(); recipe.mealType = request.mealType(); recipe.repeatedFrom = repeatedFrom; recipe.ingredients.clear(); recipe.steps.clear();
   for (int index = 0; index < request.ingredients().size(); index++) { HomeRecipeIngredientRequest source = request.ingredients().get(index); HomeRecipeIngredient ingredient = new HomeRecipeIngredient(); ingredient.recipe = recipe; ingredient.name = source.name().trim(); ingredient.quantity = source.quantity(); ingredient.unit = source.unit().trim(); ingredient.position = index; recipe.ingredients.add(ingredient); }
   for (int index = 0; index < request.steps().size(); index++) { HomeRecipeStepRequest source = request.steps().get(index); HomeRecipeStep step = new HomeRecipeStep(); step.recipe = recipe; step.instruction = source.instruction().trim(); step.position = index; recipe.steps.add(step); }
  }
  private static HomeRecipeDto recipe(HomeRecipe value, HomeRecipePhoto photo, List<HomeRecipeReviewDto> reviews, List<HomeRecipeReferenceDto> repetitions) {
   return new HomeRecipeDto(value.id, value.home, value.name, value.servings, value.recipeUrl, value.preparedOn, value.mealType, value.ingredients.stream().map(ingredient -> new HomeRecipeIngredientDto(ingredient.name, ingredient.quantity, ingredient.unit)).toList(), value.steps.stream().map(step -> new HomeRecipeStepDto(step.instruction)).toList(), value.repeatedFrom == null ? null : reference(value.repeatedFrom), repetitions, value.author.username, photo == null ? null : "/how-cook/" + value.id + "/photo?v=" + photo.id, photo == null ? null : "/how-cook/" + value.id + "/photo?thumbnail=true&v=" + photo.id, photo == null ? null : photo.width, photo == null ? null : photo.height, reviews, value.createdAt);
  }
 private HomeRecipePhoto copyPhoto(HomeRecipe recipe, HomeRecipePhoto source) {
  HomeRecipePhoto copy = new HomeRecipePhoto(); copy.recipe = recipe; copy.imageBase64 = source.imageBase64; copy.thumbnailBase64 = source.thumbnailBase64; copy.width = source.width; copy.height = source.height; copy.createdAt = Instant.now();
  return photos.save(copy);
 }
  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static HomeRecipeReferenceDto reference(HomeRecipe value) { return new HomeRecipeReferenceDto(value.id, value.name); }
 private static HomeRecipeReviewDto review(HomeRecipeReview value) { return new HomeRecipeReviewDto(value.id, value.author.username, value.rating, value.comment, value.updatedAt); }
  private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
  private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
 private static HomeRecipe owned(HomeRecipe recipe, User user) { if (user.role != Role.ADMIN && !recipe.author.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return recipe; }
}
