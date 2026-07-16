package com.wherefood.web;

import com.wherefood.config.*;
import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.security.core.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.server.ResponseStatusException;

record LoginRequest(@NotBlank String username, @NotBlank String password) {}
record AuthResponse(String token, String username, String role) {}
record CategoryRequest(@NotBlank String name, @NotBlank String slug, @NotBlank String icon, boolean active) {}
record CategoryDto(Long id, String name, String slug, String icon, boolean active) {}
record HighlightTagRequest(@NotBlank String name, @NotBlank String emoji) {}
record HighlightTagDto(Long id, String name, String emoji) {}
record PlaceRequest(@NotBlank String name, String address, String sourceUrl, String mapsUrl, @NotNull Long categoryId, List<Long> tagIds) {}
record ItemRequest(@NotBlank String name, String comment, @Min(1) @Max(5) short taste, @Min(1) @Max(5) short price, @NotNull LocalDate visitDate) {}
record PlaceReviewRequest(String comment, @Min(1) @Max(5) short location, @Min(1) @Max(5) short heating, @Min(1) @Max(5) short bathrooms, @Min(1) @Max(5) short exterior, @Min(1) @Max(5) short seating, @Min(1) @Max(5) short service, @Min(1) @Max(5) short ambiance) {}
record PlaceReviewDto(String author, String comment, short location, short heating, short bathrooms, short exterior, short seating, short service, short ambiance) {}
record ItemDto(Long id, String name, String comment, short taste, short price, String author, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, LocalDate visitDate, Instant createdAt) {}
record PlaceDto(Long id, String name, String address, String sourceUrl, String mapsUrl, PlaceStatus status, CategoryDto category, List<HighlightTagDto> tags, String author, double rating, double tasteAverage, double priceAverage, double venueAverage, long itemCount, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<PlaceReviewDto> reviews, Instant createdAt) {}
record Slice<T>(List<T> content, Long nextCursor) {}

@RestController
@RequestMapping("/api")
public class Api {
 private final Users users; private final Categories categories; private final HighlightTags highlightTags; private final Places places; private final Items items; private final Photos photos; private final PlaceReviews reviews; private final PlacePhotos placePhotos; private final JwtTokens jwt; private final PhotoStorage storage; private final org.springframework.security.crypto.password.PasswordEncoder encoder;

 public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, Items items, Photos photos, PlaceReviews reviews, PlacePhotos placePhotos, JwtTokens jwt, PhotoStorage storage, org.springframework.security.crypto.password.PasswordEncoder encoder) {
  this.users = users; this.categories = categories; this.highlightTags = highlightTags; this.places = places; this.items = items; this.photos = photos; this.reviews = reviews; this.placePhotos = placePhotos; this.jwt = jwt; this.storage = storage; this.encoder = encoder;
 }

 @PostMapping("/auth/login") AuthResponse login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
  User user = users.findByUsername(request.username().toLowerCase()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
  if (!encoder.matches(request.password(), user.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
  return new AuthResponse(jwt.token(user), user.username, user.role.name());
 }

 @GetMapping("/categories") List<CategoryDto> categories() { return categories.findByActiveTrueOrderByName().stream().map(Api::category).toList(); }
 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<CategoryDto> allCategories() { return categories.findAll().stream().map(Api::category).toList(); }
 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") CategoryDto addCategory(@RequestBody @jakarta.validation.Valid CategoryRequest request) { Category category = new Category(); apply(category, request); return category(categories.save(category)); }
 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") CategoryDto updateCategory(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CategoryRequest request) { Category category = categories.findById(id).orElseThrow(() -> notFound("Categoría")); apply(category, request); return category(categories.save(category)); }
 @GetMapping("/highlight-tags") List<HighlightTagDto> tags() { return highlightTags.findAllByOrderByNameAsc().stream().map(Api::tag).toList(); }
 @PostMapping("/highlight-tags") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto addTag(@RequestBody @jakarta.validation.Valid HighlightTagRequest request) { HighlightTag tag = new HighlightTag(); apply(tag, request); return tag(highlightTags.save(tag)); }
 @PutMapping("/highlight-tags/{id}") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto updateTag(@PathVariable Long id, @RequestBody @jakarta.validation.Valid HighlightTagRequest request) { HighlightTag tag = highlightTags.findById(id).orElseThrow(() -> notFound("Etiqueta")); apply(tag, request); return tag(highlightTags.save(tag)); }

 @GetMapping("/places") Slice<PlaceDto> list(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) PlaceStatus status, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  List<Place> candidates = places.findAll().stream().filter(place -> categoryId == null || place.category.id.equals(categoryId)).filter(place -> status == null || place.status == status).toList();
  List<Long> candidateIds = candidates.stream().map(place -> place.id).toList();
  Map<Long, PlaceMetric> metrics = metrics(candidateIds);
  Map<Long, VenueMetric> venueMetrics = venueMetrics(candidateIds);
  long offset = cursor == null ? 0 : Math.max(0, cursor);
  List<Place> result = candidates.stream().sorted(Comparator.comparingDouble((Place place) -> ranking(metrics.get(place.id), venueMetrics.get(place.id))).reversed().thenComparing(place -> place.id, Comparator.reverseOrder())).skip(offset).limit(limit + 1).toList();
  Long next = result.size() > limit ? offset + limit : null;
  List<Place> page = result.stream().limit(limit).toList();
  return new Slice<>(page.stream().map(place -> place(place, metrics.get(place.id))).toList(), next);
 }

 @PostMapping("/places") PlaceDto addPlace(@RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
  Place place = new Place(); apply(place, request); place.status = PlaceStatus.PENDING; place.category = categories.findById(request.categoryId()).filter(category -> category.active).orElseThrow(() -> notFound("Categoría")); place.createdBy = owner; place.createdAt = place.updatedAt = Instant.now(); return place(places.save(place));
 }
 @PutMapping("/places/{id}") PlaceDto editPlace(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
  Place place = owned(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")), owner); apply(place, request); place.category = categories.findById(request.categoryId()).orElseThrow(() -> notFound("Categoría")); place.updatedAt = Instant.now(); return place(places.save(place));
 }
 @DeleteMapping("/places/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlace(@PathVariable Long id, @AuthenticationPrincipal User owner) { places.delete(owned(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")), owner)); }
 @GetMapping("/places/{id}") PlaceDto getPlace(@PathVariable Long id) { Place place = places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")); return place(place, metrics(List.of(id)).get(id)); }
 @GetMapping(value = "/places/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> placePhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
  PlacePhoto photo = placePhotos.findByPlaceId(id).orElseThrow(() -> notFound("Foto"));
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PutMapping("/places/{id}/review") PlaceReviewDto saveReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceReviewRequest request, @AuthenticationPrincipal User author) {
  Place place = places.findById(id).orElseThrow(() -> notFound("Lugar"));
  PlaceReview review = reviews.findByPlaceIdAndAuthorId(id, author.id).orElseGet(() -> { PlaceReview value = new PlaceReview(); value.place = place; value.author = author; value.createdAt = Instant.now(); return value; });
  apply(review, request); review.updatedAt = Instant.now(); place.status = PlaceStatus.REVIEWED; places.save(place); return review(reviews.save(review));
 }

 @PostMapping(value = "/places/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @org.springframework.transaction.annotation.Transactional PlaceDto uploadPlacePhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
  Place place = owned(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")), user); placePhotos.findByPlaceId(id).ifPresent(placePhotos::delete); placePhotos.flush(); placePhotos.save(storage.store(place, file)); return place(place);
 }

 @GetMapping("/places/{id}/item-dates") List<LocalDate> itemDates(@PathVariable Long id) { places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")); return items.findActiveVisitDates(id); }
 @GetMapping("/places/{id}/items") Slice<ItemDto> listItems(@PathVariable Long id, @RequestParam(required = false) LocalDate visitDate) { return itemSlice(id, visitDate); }

 @PostMapping("/places/{placeId}/items") ItemDto addItem(@PathVariable Long placeId, @RequestBody @jakarta.validation.Valid ItemRequest request, @AuthenticationPrincipal User author) {
  Item item = new Item(); item.place = places.findById(placeId).orElseThrow(() -> notFound("Lugar")); item.place.status = PlaceStatus.REVIEWED; places.save(item.place); item.author = author; apply(item, request); return item(items.save(item));
 }
 @PutMapping("/items/{id}") ItemDto editItem(@PathVariable Long id, @RequestBody @jakarta.validation.Valid ItemRequest request, @AuthenticationPrincipal User author) { Item item = owned(items.findById(id).orElseThrow(() -> notFound("Ítem")), author); apply(item, request); return item(items.save(item)); }
 @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteItem(@PathVariable Long id, @AuthenticationPrincipal User author) { Item item = owned(items.findById(id).orElseThrow(() -> notFound("Ítem")), author); item.deletedAt = Instant.now(); items.save(item); }
 @PostMapping(value = "/items/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @org.springframework.transaction.annotation.Transactional ItemDto upload(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException { Item item = owned(items.findById(id).orElseThrow(() -> notFound("Ítem")), user); photos.findByItemId(id).ifPresent(photos::delete); photos.flush(); ItemPhoto photo = storage.store(item, file); photos.save(photo); return item(item, photo); }

 Slice<ItemDto> itemSlice(Long placeId, LocalDate visitDate) {
  if (!places.existsById(placeId)) throw notFound("Lugar");
  LocalDate selected = visitDate == null ? items.findActiveVisitDates(placeId).stream().findFirst().orElse(null) : visitDate;
  List<Item> page = selected == null ? List.of() : items.findByPlaceIdAndVisitDateAndDeletedAtIsNullOrderByIdDesc(placeId, selected);
  Map<Long, ItemPhoto> photoMap = photos.findByItemIdIn(page.stream().map(item -> item.id).toList()).stream().filter(photo -> photo.item != null && photo.item.id != null).collect(java.util.stream.Collectors.toMap(photo -> photo.item.id, photo -> photo, (first, ignored) -> first));
  return new Slice<>(page.stream().map(item -> item(item, photoMap.get(item.id))).toList(), null);
 }

 private Map<Long, PlaceMetric> metrics(List<Long> ids) { if (ids.isEmpty()) return Map.of(); return items.metrics(ids).stream().collect(java.util.stream.Collectors.toMap(PlaceMetric::getPlaceId, metric -> metric)); }
 private Map<Long, VenueMetric> venueMetrics(List<Long> ids) { if (ids.isEmpty()) return Map.of(); return reviews.venueMetrics(ids).stream().collect(java.util.stream.Collectors.toMap(VenueMetric::getPlaceId, metric -> metric)); }
 private PlaceDto place(Place place) { return place(place, metrics(List.of(place.id)).get(place.id)); }
 private PlaceDto place(Place place, PlaceMetric metric) {
  double taste = metric == null ? 0 : metric.getTasteAverage(); double price = metric == null ? 0 : metric.getPriceAverage(); List<PlaceReviewDto> venueReviews = reviews.findByPlaceIdOrderByAuthorUsername(place.id).stream().map(Api::review).toList(); double venue = venueReviews.stream().mapToDouble(Api::venueScore).average().orElse(0); double rating = ranking(taste, price, venue); PlacePhoto photo = placePhotos.findByPlaceId(place.id).orElse(null);
  return new PlaceDto(place.id, place.name, place.address, place.sourceUrl, place.mapsUrl, place.status, category(place.category), place.highlightTags.stream().sorted(Comparator.comparing(tag -> tag.name)).map(Api::tag).toList(), place.createdBy.username, round(rating), round(taste), round(price), round(venue), metric == null ? 0 : metric.getItemCount(), photo == null ? null : photoUrl(place.id, false), photo == null ? null : photoUrl(place.id, true), photo == null ? null : photo.width, photo == null ? null : photo.height, venueReviews, place.createdAt);
 }
 private static double venueScore(PlaceReviewDto review) { return (review.location() + review.heating() + review.bathrooms() + review.exterior() + review.seating() + review.service() + review.ambiance()) / 7d; }
 private static double ranking(PlaceMetric metric, VenueMetric venueMetric) { return ranking(metric == null ? 0 : metric.getTasteAverage(), metric == null ? 0 : metric.getPriceAverage(), venueMetric == null ? 0 : venueMetric.getVenueAverage()); }
 private static double ranking(double taste, double price, double venue) { int parts = (taste > 0 ? 1 : 0) + (price > 0 ? 1 : 0) + (venue > 0 ? 1 : 0); return parts == 0 ? 0 : (taste + price + venue) / parts; }
 private static String photoUrl(Long placeId, boolean thumbnail) { return "/places/" + placeId + "/photo" + (thumbnail ? "?thumbnail=true" : ""); }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
 private void apply(Place place, PlaceRequest request) { place.name = request.name(); place.address = request.address(); place.sourceUrl = request.sourceUrl(); place.mapsUrl = request.mapsUrl(); place.highlightTags.clear(); if (request.tagIds() != null && !request.tagIds().isEmpty()) { Set<Long> ids = new LinkedHashSet<>(request.tagIds()); List<HighlightTag> selected = highlightTags.findAllById(ids); if (selected.size() != ids.size()) throw notFound("Etiqueta"); place.highlightTags.addAll(selected); } }
 private static void apply(PlaceReview review, PlaceReviewRequest request) { review.comment = request.comment(); review.location = request.location(); review.heating = request.heating(); review.bathrooms = request.bathrooms(); review.exterior = request.exterior(); review.seating = request.seating(); review.service = request.service(); review.ambiance = request.ambiance(); }
 private ItemDto item(Item item) { return item(item, photos.findByItemId(item.id).orElse(null)); }
 private ItemDto item(Item item, ItemPhoto photo) { return new ItemDto(item.id, item.name, item.comment, item.taste, item.price, item.author.username, photo == null ? null : storage.url(photo.imageBase64), photo == null ? null : storage.url(photo.thumbnailBase64), photo == null ? null : photo.width, photo == null ? null : photo.height, item.visitDate, item.createdAt); }
 private static PlaceReviewDto review(PlaceReview review) { return new PlaceReviewDto(review.author.username, review.comment, review.location, review.heating, review.bathrooms, review.exterior, review.seating, review.service, review.ambiance); }
 private static CategoryDto category(Category category) { return new CategoryDto(category.id, category.name, category.slug, category.icon, category.active); }
 private static HighlightTagDto tag(HighlightTag tag) { return new HighlightTagDto(tag.id, tag.name, tag.emoji); }
 private static void apply(Category category, CategoryRequest request) { category.name = request.name(); category.slug = request.slug(); category.icon = request.icon(); category.active = request.active(); }
 private static void apply(HighlightTag tag, HighlightTagRequest request) { tag.name = request.name().trim(); tag.emoji = request.emoji().trim(); }
 private static void apply(Item item, ItemRequest request) { item.name = request.name(); item.comment = request.comment(); item.taste = request.taste(); item.price = request.price(); item.visitDate = request.visitDate(); item.updatedAt = Instant.now(); if (item.createdAt == null) item.createdAt = item.updatedAt; }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static Place owned(Place place, User user) { if (!place.createdBy.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return place; }
 private static Item owned(Item item, User user) { if (!item.author.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return item; }
}
