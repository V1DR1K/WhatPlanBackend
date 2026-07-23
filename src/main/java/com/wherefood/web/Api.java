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
record VisitRequest(@NotNull LocalDate visitedOn) {}
record ItemRequest(@NotBlank String name) {}
record ItemReviewRequest(String comment, @Min(1) @Max(5) short taste, @Min(1) @Max(5) short price) {}
record CreateItemRequest(@NotBlank String name) {}
record PlaceReviewRequest(String comment, @Min(1) @Max(5) Short location, @Min(1) @Max(5) Short heating, @Min(1) @Max(5) Short bathrooms, @Min(1) @Max(5) Short exterior, @Min(1) @Max(5) Short seating, @Min(1) @Max(5) Short service, @Min(1) @Max(5) Short ambiance) {}
record PlaceReviewDto(String author, String comment, Short location, Short heating, Short bathrooms, Short exterior, Short seating, Short service, Short ambiance) {}
record ItemReviewDto(String author, String comment, short taste, short price, Instant createdAt, Instant updatedAt) {}
record ItemDto(Long id, String name, String createdBy, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<ItemReviewDto> reviews, Instant createdAt) {}
record PlaceVisitSummaryDto(Long id, LocalDate visitedOn, String createdBy, Instant createdAt) {}
record PlaceVisitPhotoDto(Long id, String url, String thumbnailUrl, int width, int height, int position, String createdBy, Instant createdAt) {}
record PlaceVisitReviewRequest(@NotNull @Min(1) @Max(5) Short overall, @Size(max = 2000) String comment, @Min(1) @Max(5) Short taste, @Min(1) @Max(5) Short price) {}
record PlaceVisitReviewDto(Long id, String author, String updatedBy, short overall, String comment, Short taste, Short price, Instant createdAt, Instant updatedAt) {}
record PlaceVisitDto(Long id, Long placeId, LocalDate visitedOn, String createdBy, List<ItemDto> items, List<PlaceVisitPhotoDto> photos, PlaceVisitPhotoDto coverPhoto, List<PlaceVisitReviewDto> reviews, String updatedBy, Instant createdAt, Instant updatedAt) {}
record PlaceDto(Long id, String name, String address, String sourceUrl, String mapsUrl, PlaceStatus status, CategoryDto category, List<HighlightTagDto> tags, String author, double rating, double tasteAverage, double priceAverage, double venueAverage, long itemCount, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<PlaceReviewDto> reviews, Instant createdAt) {}
record Slice<T>(List<T> content, Long nextCursor) {}

@RestController
@RequestMapping("/api")
public class Api {
 private static final int MAX_VISIT_PHOTOS = 12;
 private final Users users; private final Categories categories; private final HighlightTags highlightTags; private final Places places; private final PlaceVisits visits; private final Items items; private final Photos photos; private final ItemReviews itemReviews; private final PlaceReviews reviews; private final PlacePhotos placePhotos; private final PlaceVisitPhotos visitPhotos; private final PlaceVisitReviews visitReviews; private final JwtTokens jwt; private final PhotoStorage storage; private final org.springframework.security.crypto.password.PasswordEncoder encoder;

  public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, JwtTokens jwt, PhotoStorage storage, org.springframework.security.crypto.password.PasswordEncoder encoder) {
   this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, null, null, jwt, storage, encoder);
  }
  @org.springframework.beans.factory.annotation.Autowired public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, JwtTokens jwt, PhotoStorage storage, org.springframework.security.crypto.password.PasswordEncoder encoder) {
   this.users = users; this.categories = categories; this.highlightTags = highlightTags; this.places = places; this.visits = visits; this.items = items; this.photos = photos; this.itemReviews = itemReviews; this.reviews = reviews; this.placePhotos = placePhotos; this.visitPhotos = visitPhotos; this.visitReviews = visitReviews; this.jwt = jwt; this.storage = storage; this.encoder = encoder;
  }

 @PostMapping("/auth/login") AuthResponse login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
  User user = users.findByUsername(request.username().toLowerCase()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
  if (!encoder.matches(request.password(), user.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
  return new AuthResponse(jwt.token(user), user.username, user.role.name());
 }

 @GetMapping("/categories") List<CategoryDto> categories() { return categories.findByActiveTrueOrderByName().stream().map(Api::category).toList(); }
 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<CategoryDto> allCategories() { return categories.findAll().stream().map(Api::category).toList(); }
 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") CategoryDto addCategory(@RequestBody @jakarta.validation.Valid CategoryRequest request) { Category category = new Category(); apply(category, request); category.createdAt = Instant.now(); return category(categories.save(category)); }
 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") CategoryDto updateCategory(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CategoryRequest request) { Category category = categories.findById(id).orElseThrow(() -> notFound("Categoría")); apply(category, request); return category(categories.save(category)); }
 @GetMapping("/highlight-tags") List<HighlightTagDto> tags() { return highlightTags.findAllByOrderByNameAsc().stream().map(Api::tag).toList(); }
 @PostMapping("/highlight-tags") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto addTag(@RequestBody @jakarta.validation.Valid HighlightTagRequest request) { HighlightTag tag = new HighlightTag(); apply(tag, request); return tag(highlightTags.save(tag)); }
 @PutMapping("/highlight-tags/{id}") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto updateTag(@PathVariable Long id, @RequestBody @jakarta.validation.Valid HighlightTagRequest request) { HighlightTag tag = highlightTags.findById(id).orElseThrow(() -> notFound("Etiqueta")); apply(tag, request); return tag(highlightTags.save(tag)); }

 @GetMapping("/places") Slice<PlaceDto> list(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long highlightTagId, @RequestParam(required = false) PlaceStatus status, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  List<Place> candidates = places.findAll().stream().filter(place -> place.deactivatedAt == null).filter(place -> categoryId == null || place.category.id.equals(categoryId)).filter(place -> highlightTagId == null || place.highlightTags.stream().anyMatch(tag -> tag.id.equals(highlightTagId))).filter(place -> status == null || place.status == status).toList();
  Map<Long, PlaceSummary> summaries = placeSummaries(candidates);
  long offset = cursor == null ? 0 : Math.max(0, cursor);
  List<Place> result = candidates.stream().sorted(Comparator.comparingDouble((Place place) -> summaries.get(place.id).rating()).reversed().thenComparing(place -> place.id, Comparator.reverseOrder())).skip(offset).limit(limit + 1).toList();
  Long next = result.size() > limit ? offset + limit : null;
  List<Place> page = result.stream().limit(limit).toList();
  return new Slice<>(page.stream().map(place -> place(place, summaries.get(place.id))).toList(), next);
 }

  @PostMapping("/places") PlaceDto addPlace(@RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
   Place place = new Place(); apply(place, request); place.status = PlaceStatus.PENDING; place.category = categories.findById(request.categoryId()).filter(category -> category.active).orElseThrow(() -> notFound("Categoría")); place.createdBy = place.updatedBy = owner; place.createdAt = place.updatedAt = Instant.now(); return place(places.save(place));
  }
  @PutMapping("/places/{id}") PlaceDto editPlace(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
   Place place = active(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar"))); apply(place, request); place.category = categories.findById(request.categoryId()).orElseThrow(() -> notFound("Categoría")); place.updatedBy = owner; place.updatedAt = Instant.now(); return place(places.save(place));
  }
   @DeleteMapping("/places/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlace(@PathVariable Long id, @AuthenticationPrincipal User owner) { Place place = active(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar"))); place.deactivatedAt = place.updatedAt = Instant.now(); place.updatedBy = owner; places.save(place); }
   @GetMapping("/places/archived") List<PlaceDto> archivedPlaces() { List<Place> archived = places.findAll().stream().filter(place -> place.deactivatedAt != null).toList(); Map<Long, PlaceSummary> summaries = placeSummaries(archived); return archived.stream().map(place -> place(place, summaries.get(place.id))).toList(); }
   @PostMapping("/places/{id}/restore") PlaceDto restorePlace(@PathVariable Long id, @AuthenticationPrincipal User owner) { Place place = places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")); place.deactivatedAt = null; place.updatedBy = owner; place.updatedAt = Instant.now(); return place(places.save(place)); }
  @GetMapping("/places/{id}") PlaceDto getPlace(@PathVariable Long id) { Place place = active(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar"))); return place(place, placeSummaries(List.of(place)).get(id)); }
 @GetMapping(value = "/places/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> placePhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
  active(places.findById(id).orElseThrow(() -> notFound("Lugar"))); PlacePhoto photo = placePhotos.findByPlaceId(id).orElseThrow(() -> notFound("Foto"));
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PutMapping("/places/{id}/review") PlaceReviewDto saveReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceReviewRequest request, @AuthenticationPrincipal User author) {
  Place place = active(places.findById(id).orElseThrow(() -> notFound("Lugar")));
  PlaceReview review = reviews.findByPlaceIdAndAuthorId(id, author.id).orElseGet(() -> { PlaceReview value = new PlaceReview(); value.place = place; value.author = author; value.createdAt = Instant.now(); return value; });
   apply(review, request); review.updatedAt = Instant.now(); return review(reviews.save(review));
 }

 @PostMapping(value = "/places/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @org.springframework.transaction.annotation.Transactional PlaceDto uploadPlacePhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
   Place place = active(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar"))); placePhotos.findByPlaceId(id).ifPresent(placePhotos::delete); placePhotos.flush(); place.updatedBy = user; place.updatedAt = Instant.now(); places.save(place); placePhotos.save(storage.store(place, file)); return place(place);
 }

 @GetMapping("/places/{id}/visits") List<PlaceVisitSummaryDto> listVisits(@PathVariable Long id) {
  active(places.findDetailedById(id).orElseThrow(() -> notFound("Lugar")));
    return visits.findByPlaceIdOrderByVisitedOnDescIdDesc(id).stream().map(Api::visitSummary).toList();
 }
  @PostMapping("/places/{id}/visits") @ResponseStatus(HttpStatus.CREATED) PlaceVisitSummaryDto addVisit(@PathVariable Long id, @RequestBody @jakarta.validation.Valid VisitRequest request, @AuthenticationPrincipal User author) {
   Place place = active(places.findById(id).orElseThrow(() -> notFound("Lugar")));
   validateVisitMoment(request);
   if (visits.findByPlaceIdAndVisitedOn(id, request.visitedOn()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una visita para esa fecha");
     PlaceVisit visit = new PlaceVisit(); visit.place = place; visit.visitedOn = request.visitedOn(); visit.createdBy = visit.updatedBy = author; visit.createdAt = visit.updatedAt = Instant.now(); place.status = PlaceStatus.REVIEWED; place.updatedBy = author; places.save(place);
   return visitSummary(visits.save(visit));
  }
  @PutMapping("/place-visits/{id}") PlaceVisitSummaryDto editVisit(@PathVariable Long id, @RequestBody @jakarta.validation.Valid VisitRequest request, @AuthenticationPrincipal User author) {
    PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
   validateVisitMoment(request);
   visits.findByPlaceIdAndVisitedOn(visit.place.id, request.visitedOn()).filter(other -> !other.id.equals(visit.id)).ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una visita para esa fecha"); });
     visit.visitedOn = request.visitedOn(); visit.updatedBy = author; visit.updatedAt = Instant.now(); return visitSummary(visits.save(visit));
  }
  @DeleteMapping("/place-visits/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @org.springframework.transaction.annotation.Transactional void deleteVisit(@PathVariable Long id, @AuthenticationPrincipal User author) { PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita"))); Place place = visit.place; visits.delete(visit); if (!visits.existsByPlaceId(place.id)) { place.status = PlaceStatus.PENDING; place.updatedBy = author; places.save(place); } }
 @GetMapping("/place-visits/{id}") PlaceVisitDto getVisit(@PathVariable Long id) { return visit(active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")))); }

 @PostMapping("/place-visits/{id}/items") ItemDto addItem(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CreateItemRequest request, @AuthenticationPrincipal User author) {
  PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
  Item item = new Item(); item.visit = visit; item.createdBy = author; apply(item, new ItemRequest(request.name())); return item(items.save(item));
 }
   @PutMapping("/items/{id}") @org.springframework.transaction.annotation.Transactional ItemDto editItem(@PathVariable Long id, @RequestBody @jakarta.validation.Valid ItemRequest request, @AuthenticationPrincipal User author) { Item item = active(items.findById(id).orElseThrow(() -> notFound("Ítem"))); apply(item, request); items.save(item); return item(item); }
   @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteItem(@PathVariable Long id, @AuthenticationPrincipal User author) { Item item = active(items.findById(id).orElseThrow(() -> notFound("Ítem"))); item.deletedAt = Instant.now(); items.save(item); }
 @PutMapping("/items/{id}/reviews/me") ItemReviewDto saveItemReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid ItemReviewRequest request, @AuthenticationPrincipal User author) {
  Item item = active(items.findById(id).filter(value -> value.deletedAt == null).orElseThrow(() -> notFound("Ítem")));
  ItemReview review = itemReviews.findByItemIdAndAuthorId(id, author.id).orElseGet(() -> { ItemReview value = new ItemReview(); value.item = item; value.author = author; value.createdAt = Instant.now(); return value; });
  apply(review, request); review.updatedAt = Instant.now(); return itemReview(itemReviews.save(review));
 }
  @PostMapping(value = "/items/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @org.springframework.transaction.annotation.Transactional ItemDto upload(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException { Item item = active(items.findById(id).orElseThrow(() -> notFound("Ítem"))); photos.findByItemId(id).ifPresent(photos::delete); photos.flush(); ItemPhoto photo = storage.store(item, file); photos.save(photo); return item(item, photo); }
  @GetMapping(value = "/items/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> itemPhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   active(items.findById(id).orElseThrow(() -> notFound("Ítem"))); ItemPhoto photo = photos.findByItemId(id).orElseThrow(() -> notFound("Foto"));
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }

  @PostMapping(value = "/place-visits/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @org.springframework.transaction.annotation.Transactional PlaceVisitDto uploadVisitPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
   List<PlaceVisitPhoto> current = visitPhotos.findByVisitIdOrderByPositionAscIdAsc(id);
   if (current.size() >= MAX_VISIT_PHOTOS) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cada visita admite hasta " + MAX_VISIT_PHOTOS + " fotos");
   PlaceVisitPhoto photo = visitPhotos.saveAndFlush(storage.store(visit, author, current.isEmpty() ? 0 : current.getLast().position + 1, file));
   if (visit.coverPhotoId == null) { visit.coverPhotoId = photo.id; visit.updatedBy = author; visit.updatedAt = Instant.now(); visits.save(visit); }
   List<PlaceVisitPhoto> responsePhotos = new ArrayList<>(current); responsePhotos.add(photo);
   return visit(visit, responsePhotos);
  }
  @PutMapping("/place-visits/{id}/cover/{photoId}") @org.springframework.transaction.annotation.Transactional PlaceVisitDto setVisitCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
   PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
   PlaceVisitPhoto photo = visitPhotos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto"));
   if (!photo.visit.id.equals(visit.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto no pertenece a esta visita");
   visit.coverPhotoId = photo.id; visit.updatedBy = author; visit.updatedAt = Instant.now(); return visit(visits.save(visit));
  }
  @DeleteMapping("/place-visit-photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @org.springframework.transaction.annotation.Transactional void deleteVisitPhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
   PlaceVisitPhoto photo = visitPhotos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); PlaceVisit visit = photo.visit;
   boolean wasCover = photo.id.equals(visit.coverPhotoId); visitPhotos.delete(photo); visitPhotos.flush();
   if (wasCover) { visit.coverPhotoId = visitPhotos.findByVisitIdOrderByPositionAscIdAsc(visit.id).stream().findFirst().map(value -> value.id).orElse(null); visit.updatedBy = author; visit.updatedAt = Instant.now(); visits.save(visit); }
  }
  @GetMapping(value = "/place-visit-photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> visitPhoto(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
   PlaceVisitPhoto photo = visitPhotos.findById(photoId).orElseThrow(() -> notFound("Foto"));
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping("/place-visits/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) @org.springframework.transaction.annotation.Transactional PlaceVisitReviewDto addVisitReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
   if (visitReviews.findByVisitIdAndAuthorId(id, author.id).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una reseña de este autor para la visita");
   PlaceVisitReview review = new PlaceVisitReview(); review.visit = visit; review.author = review.updatedBy = author; review.createdAt = review.updatedAt = Instant.now(); apply(review, request); return visitReview(visitReviews.save(review));
  }
  @PutMapping("/place-visits/{id}/reviews/me") @org.springframework.transaction.annotation.Transactional PlaceVisitReviewDto saveOwnVisitReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   PlaceVisit visit = active(visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")));
   PlaceVisitReview review = visitReviews.findByVisitIdAndAuthorId(id, author.id).orElseGet(() -> { PlaceVisitReview value = new PlaceVisitReview(); value.visit = visit; value.author = author; value.createdAt = Instant.now(); return value; });
   review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return visitReview(visitReviews.save(review));
  }
  @PutMapping("/place-visit-reviews/{reviewId}") @org.springframework.transaction.annotation.Transactional PlaceVisitReviewDto updateVisitReview(@PathVariable Long reviewId, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   PlaceVisitReview review = visitReviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña")); active(review.visit); review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return visitReview(visitReviews.save(review));
  }
  @DeleteMapping("/place-visit-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteVisitReview(@PathVariable Long reviewId) { visitReviews.delete(visitReviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña"))); }

  private Map<Long, PlaceSummary> placeSummaries(List<Place> values) {
   if (values.isEmpty()) return Map.of();
   List<Long> placeIds = values.stream().map(place -> place.id).toList();
   List<PlaceVisit> allVisits = visits.findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(placeIds);
   Map<Long, List<PlaceVisit>> visitsByPlace = allVisits.stream().collect(java.util.stream.Collectors.groupingBy(visit -> visit.place.id, LinkedHashMap::new, java.util.stream.Collectors.toList()));
   List<Long> visitIds = allVisits.stream().map(visit -> visit.id).toList();
   Map<Long, List<PlaceVisitReview>> reviewsByVisit = visitIds.isEmpty() ? Map.of() : visitReviews.findByVisitIdInOrderByVisitIdAscAuthorUsername(visitIds).stream().collect(java.util.stream.Collectors.groupingBy(review -> review.visit.id));
   Map<Long, List<PlaceReviewDto>> reviewsByPlace = reviews.summariesByPlaceIdIn(placeIds).stream().collect(java.util.stream.Collectors.groupingBy(PlaceReviewSummary::getPlaceId, java.util.stream.Collectors.mapping(Api::review, java.util.stream.Collectors.toList())));
   Map<Long, List<PlaceVisitPhoto>> photosByVisit = visitIds.isEmpty() ? Map.of() : visitPhotos.findByVisitIdInOrderByVisitIdAscPositionAscIdAsc(visitIds).stream().collect(java.util.stream.Collectors.groupingBy(photo -> photo.visit.id));
   Map<Long, PlacePhoto> legacyPhotos = placePhotos.findByPlaceIdIn(placeIds).stream().collect(java.util.stream.Collectors.toMap(photo -> photo.place.id, photo -> photo));
   Map<Long, PlaceSummary> result = new HashMap<>();
   for (Place place : values) {
    List<PlaceVisit> placeVisits = visitsByPlace.getOrDefault(place.id, List.of());
    List<PlaceVisitReview> visitReviewsForPlace = placeVisits.stream().flatMap(visit -> reviewsByVisit.getOrDefault(visit.id, List.of()).stream()).toList();
    List<PlaceReviewDto> placeReviews = reviewsByPlace.getOrDefault(place.id, List.of());
    PlaceVisitPhoto cover = placeVisits.stream().map(visit -> photosByVisit.getOrDefault(visit.id, List.of()).stream().filter(photo -> photo.id.equals(visit.coverPhotoId)).findFirst().orElseGet(() -> photosByVisit.getOrDefault(visit.id, List.of()).stream().findFirst().orElse(null))).filter(Objects::nonNull).findFirst().orElse(null);
    double rating = visitReviewsForPlace.stream().mapToInt(review -> review.overall).average().orElse(0);
    double taste = visitReviewsForPlace.stream().map(review -> review.taste).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    double price = visitReviewsForPlace.stream().map(review -> review.price).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    double venue = placeReviews.stream().flatMap(review -> java.util.stream.Stream.of(review.location(), review.heating(), review.bathrooms(), review.exterior(), review.seating(), review.service(), review.ambiance())).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    result.put(place.id, new PlaceSummary(rating, taste, price, venue, placeVisits.size(), cover, legacyPhotos.get(place.id), placeReviews));
   }
   return result;
  }
  private PlaceDto place(Place place) { return place(place, placeSummaries(List.of(place)).get(place.id)); }
  private PlaceDto place(Place place, PlaceSummary summary) {
    PlaceVisitPhoto cover = summary.cover(); PlacePhoto profilePhoto = summary.legacyPhoto();
    String photoUrl = profilePhoto != null ? photoUrl(place.id, false, profilePhoto.id) : cover == null ? null : visitPhotoUrl(cover.id, false);
    String thumbnailUrl = profilePhoto != null ? photoUrl(place.id, true, profilePhoto.id) : cover == null ? null : visitPhotoUrl(cover.id, true);
    Integer width = profilePhoto != null ? Integer.valueOf(profilePhoto.width) : cover == null ? null : Integer.valueOf(cover.width);
    Integer height = profilePhoto != null ? Integer.valueOf(profilePhoto.height) : cover == null ? null : Integer.valueOf(cover.height);
   return new PlaceDto(place.id, place.name, place.address, place.sourceUrl, place.mapsUrl, place.status, category(place.category), place.highlightTags.stream().sorted(Comparator.comparing(tag -> tag.name)).map(Api::tag).toList(), place.createdBy.username, round(summary.rating()), round(summary.taste()), round(summary.price()), round(summary.venue()), summary.visitCount(), photoUrl, thumbnailUrl, width, height, summary.reviews(), place.createdAt);
  }
  private record PlaceSummary(double rating, double taste, double price, double venue, long visitCount, PlaceVisitPhoto cover, PlacePhoto legacyPhoto, List<PlaceReviewDto> reviews) {}
  private static String photoUrl(Long placeId, boolean thumbnail, Long photoId) { return "/places/" + placeId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static String visitPhotoUrl(Long photoId, boolean thumbnail) { return "/place-visit-photos/" + photoId + (thumbnail ? "?thumbnail=true" : ""); }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
 private void apply(Place place, PlaceRequest request) { place.name = request.name(); place.address = request.address(); place.sourceUrl = request.sourceUrl(); place.mapsUrl = request.mapsUrl(); place.highlightTags.clear(); if (request.tagIds() != null && !request.tagIds().isEmpty()) { Set<Long> ids = new LinkedHashSet<>(request.tagIds()); List<HighlightTag> selected = highlightTags.findAllById(ids); if (selected.size() != ids.size()) throw notFound("Etiqueta"); place.highlightTags.addAll(selected); } }
  private static void apply(PlaceReview review, PlaceReviewRequest request) { if (java.util.stream.Stream.of(request.location(), request.heating(), request.bathrooms(), request.exterior(), request.seating(), request.service(), request.ambiance()).allMatch(Objects::isNull)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificá al menos un aspecto del lugar"); review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim(); review.location = request.location(); review.heating = request.heating(); review.bathrooms = request.bathrooms(); review.exterior = request.exterior(); review.seating = request.seating(); review.service = request.service(); review.ambiance = request.ambiance(); }
  private PlaceVisitDto visit(PlaceVisit visit) { return visit(visit, visitPhotos.findByVisitIdOrderByPositionAscIdAsc(visit.id)); }
  private PlaceVisitDto visit(PlaceVisit visit, List<PlaceVisitPhoto> currentPhotos) {
   List<Item> visitItems = items.findByVisitIdAndDeletedAtIsNullOrderByIdDesc(visit.id);
   List<Long> itemIds = visitItems.stream().map(item -> item.id).toList();
   Map<Long, String> itemReviewAuthors = itemIds.isEmpty() ? Map.of() : itemReviews.authorsByItemIdIn(itemIds).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   Map<Long, ItemPhoto> photoMap = photos.findByItemIdIn(visitItems.stream().map(item -> item.id).toList()).stream().filter(photo -> photo.item != null && photo.item.id != null).collect(java.util.stream.Collectors.toMap(photo -> photo.item.id, photo -> photo, (first, ignored) -> first));
   List<PlaceVisitPhotoDto> resultPhotos = currentPhotos.stream().map(Api::visitPhoto).toList();
   PlaceVisitPhotoDto cover = resultPhotos.stream().filter(photo -> photo.id().equals(visit.coverPhotoId)).findFirst().orElse(resultPhotos.isEmpty() ? null : resultPhotos.getFirst());
   List<PlaceVisitReview> reviewValues = visitReviews.findByVisitIdOrderByAuthorUsername(visit.id);
   Map<Long, String> reviewAuthors = visitReviews.authorsByVisitIdIn(List.of(visit.id)).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   List<PlaceVisitReviewDto> currentReviews = reviewValues.stream().map(review -> visitReview(review, reviewAuthors.get(review.id))).toList();
    return new PlaceVisitDto(visit.id, visit.place.id, visit.visitedOn, visit.createdBy.username, visitItems.stream().map(item -> item(item, photoMap.get(item.id), itemReviewAuthors)).toList(), resultPhotos, cover, currentReviews, visit.updatedBy.username, visit.createdAt, visit.updatedAt);
  }
 private ItemDto item(Item item) { return item(item, photos.findByItemId(item.id).orElse(null)); }
 private ItemDto item(Item item, ItemPhoto photo) { return item(item, photo, itemReviews.authorsByItemIdIn(List.of(item.id)).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor))); }
 private ItemDto item(Item item, ItemPhoto photo, Map<Long, String> reviewAuthors) { return new ItemDto(item.id, item.name, item.createdBy.username, photo == null ? null : itemPhotoUrl(item.id, false, photo.id), photo == null ? null : itemPhotoUrl(item.id, true, photo.id), photo == null ? null : Integer.valueOf(photo.width), photo == null ? null : Integer.valueOf(photo.height), item.reviews.stream().sorted(Comparator.comparing(review -> reviewAuthors.get(review.id), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).map(review -> itemReview(review, reviewAuthors.get(review.id))).toList(), item.createdAt); }
  private static String itemPhotoUrl(Long itemId, boolean thumbnail, Long photoId) { return "/items/" + itemId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static PlaceVisitSummaryDto visitSummary(PlaceVisit visit) { return new PlaceVisitSummaryDto(visit.id, visit.visitedOn, visit.createdBy.username, visit.createdAt); }
  private static ItemReviewDto itemReview(ItemReview review) { return itemReview(review, review.author.username); }
  private static ItemReviewDto itemReview(ItemReview review, String author) { return new ItemReviewDto(author, review.comment, review.taste, review.price, review.createdAt, review.updatedAt); }
  private static PlaceVisitPhotoDto visitPhoto(PlaceVisitPhoto photo) { return new PlaceVisitPhotoDto(photo.id, "/place-visit-photos/" + photo.id, "/place-visit-photos/" + photo.id + "?thumbnail=true", photo.width, photo.height, photo.position, photo.createdBy.username, photo.createdAt); }
  private static PlaceVisitReviewDto visitReview(PlaceVisitReview review) { return visitReview(review, review.author.username); }
  private static PlaceVisitReviewDto visitReview(PlaceVisitReview review, String author) { return new PlaceVisitReviewDto(review.id, author, review.updatedBy.username, review.overall, review.comment, review.taste, review.price, review.createdAt, review.updatedAt); }
  private static PlaceReviewDto review(PlaceReview review) { return new PlaceReviewDto(review.author.username, review.comment, review.location, review.heating, review.bathrooms, review.exterior, review.seating, review.service, review.ambiance); }
  private static PlaceReviewDto review(PlaceReviewSummary review) { return new PlaceReviewDto(review.getAuthor(), review.getComment(), review.getLocation(), review.getHeating(), review.getBathrooms(), review.getExterior(), review.getSeating(), review.getService(), review.getAmbiance()); }
 private static CategoryDto category(Category category) { return new CategoryDto(category.id, category.name, category.slug, category.icon, category.active); }
 private static HighlightTagDto tag(HighlightTag tag) { return new HighlightTagDto(tag.id, tag.name, tag.emoji); }
 private static void apply(Category category, CategoryRequest request) { category.name = request.name(); category.slug = request.slug(); category.icon = request.icon(); category.active = request.active(); }
 private static void apply(HighlightTag tag, HighlightTagRequest request) { tag.name = request.name().trim(); tag.emoji = request.emoji().trim(); }
 private static void apply(Item item, ItemRequest request) { item.name = request.name().trim(); item.updatedAt = Instant.now(); if (item.createdAt == null) item.createdAt = item.updatedAt; }
  private static void apply(ItemReview review, ItemReviewRequest request) { review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim(); review.taste = request.taste(); review.price = request.price(); }
  private static void apply(PlaceVisitReview review, PlaceVisitReviewRequest request) { review.overall = request.overall(); review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim(); review.taste = request.taste(); review.price = request.price(); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static Place active(Place place) { if (place.deactivatedAt != null) throw notFound("Lugar"); return place; }
  private static PlaceVisit active(PlaceVisit visit) { active(visit.place); return visit; }
  private static Item active(Item item) { active(item.visit.place); return item; }
   private static void validateVisitMoment(VisitRequest request) { if (request.visitedOn().isAfter(RosarioClock.today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una visita no puede quedar en el futuro"); }
}
