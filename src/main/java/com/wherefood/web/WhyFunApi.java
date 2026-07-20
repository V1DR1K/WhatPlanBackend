package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record FunCategoryRequest(Long parentId, @NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String icon, boolean active) {}
record FunCategoryDto(Long id, Long parentId, String name, String slug, String icon, boolean active) {}
record FunScheduleRequest(@NotNull DayOfWeek day, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
record FunScheduleDto(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {}
record FunVenueRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 250) String address, @NotNull Long categoryId, @NotNull Long subcategoryId, @NotEmpty List<@Valid FunScheduleRequest> schedules) {}
record FunPhotoDto(Long id, String url, String thumbnailUrl, int width, int height) {}
record FunReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record FunReviewDto(Long id, String author, short rating, String comment, Instant updatedAt) {}
record FunVenueDto(Long id, String name, String address, FunCategoryDto category, FunCategoryDto subcategory, String author, double rating, int reviewCount, FunPhotoDto coverPhoto, List<FunScheduleDto> schedules, List<FunPhotoDto> photos, List<FunReviewDto> reviews, Instant createdAt, Instant updatedAt) {}

@RestController
@RequestMapping("/api/why-fun")
public class WhyFunApi {
 private static final int MAX_PHOTOS = 12;
 private final WhyFunCategories categories;
 private final WhyFunVenues venues;
 private final WhyFunVenuePhotos photos;
 private final WhyFunVenueReviews reviews;
 private final PhotoStorage storage;

 public WhyFunApi(WhyFunCategories categories, WhyFunVenues venues, WhyFunVenuePhotos photos, WhyFunVenueReviews reviews, PhotoStorage storage) {
  this.categories = categories; this.venues = venues; this.photos = photos; this.reviews = reviews; this.storage = storage;
 }

 @GetMapping("/categories") List<FunCategoryDto> activeCategories() {
  return categories.findAllByOrderByParentIdAscNameAsc().stream().filter(category -> category.active && (category.parent == null || category.parent.active)).map(WhyFunApi::category).toList();
 }

 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<FunCategoryDto> allCategories() {
  return categories.findAllByOrderByParentIdAscNameAsc().stream().map(WhyFunApi::category).toList();
 }

 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") @Transactional FunCategoryDto addCategory(@RequestBody @Valid FunCategoryRequest request) {
  WhyFunCategory category = new WhyFunCategory();
  apply(category, request, null);
  category.createdAt = category.updatedAt = Instant.now();
  return category(categories.save(category));
 }

 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional FunCategoryDto updateCategory(@PathVariable Long id, @RequestBody @Valid FunCategoryRequest request) {
  WhyFunCategory category = findCategory(id);
  Long currentParentId = category.parent == null ? null : category.parent.id;
  Long nextParentId = request.parentId();
  if (!Objects.equals(currentParentId, nextParentId) && (categories.existsByParentId(id) || venues.countBySubcategoryId(id) > 0)) {
   throw conflict("No podés cambiar la jerarquía de una categoría que ya tiene subcategorías o lugares");
  }
  apply(category, request, category);
  category.updatedAt = Instant.now();
  return category(categories.save(category));
 }

 @GetMapping("/venues") Slice<FunVenueDto> listVenues(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long subcategoryId, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  List<WhyFunVenue> values = venues.list(categoryId, subcategoryId, cursor, org.springframework.data.domain.PageRequest.of(0, limit + 1));
  boolean hasNext = values.size() > limit;
  List<WhyFunVenue> page = hasNext ? values.subList(0, limit) : values;
  List<Long> ids = page.stream().map(venue -> venue.id).toList();
  Map<Long, FunPhotoDto> covers = covers(ids);
  Map<Long, List<FunReviewDto>> reviewMap = reviewMap(ids);
  Long nextCursor = hasNext ? page.getLast().id : null;
  return new Slice<>(page.stream().map(venue -> venue(venue, covers.get(venue.id), List.of(), List.of(), reviewMap.getOrDefault(venue.id, List.of()))).toList(), nextCursor);
 }

 @GetMapping("/venues/{id}") FunVenueDto getVenue(@PathVariable Long id) {
  return venue(findVenue(id));
 }

 @PostMapping("/venues") @ResponseStatus(HttpStatus.CREATED) @Transactional FunVenueDto addVenue(@RequestBody @Valid FunVenueRequest request, @AuthenticationPrincipal User author) {
  WhyFunVenue venue = new WhyFunVenue();
  venue.createdBy = author;
  apply(venue, request);
  venue.createdAt = venue.updatedAt = Instant.now();
  return venue(venues.save(venue));
 }

 @PutMapping("/venues/{id}") @Transactional FunVenueDto updateVenue(@PathVariable Long id, @RequestBody @Valid FunVenueRequest request, @AuthenticationPrincipal User author) {
  WhyFunVenue venue = owned(findVenue(id), author);
  apply(venue, request);
  venue.updatedAt = Instant.now();
  return venue(venues.save(venue));
 }

 @DeleteMapping("/venues/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteVenue(@PathVariable Long id, @AuthenticationPrincipal User author) {
  venues.delete(owned(findVenue(id), author));
 }

 @PostMapping(value = "/venues/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional FunVenueDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
  WhyFunVenue venue = owned(findVenue(id), author);
  if (photos.countByVenueId(id) >= MAX_PHOTOS) throw conflict("Cada lugar admite hasta " + MAX_PHOTOS + " fotos");
  photos.save(storage.store(venue, file));
  return venue(venue);
 }

 @DeleteMapping("/photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
  WhyFunVenuePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto"));
  photos.delete(owned(photo, author));
 }

 @GetMapping(value = "/photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
  WhyFunVenuePhoto photo = photos.findById(photoId).orElseThrow(() -> notFound("Foto"));
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PutMapping("/venues/{id}/review") @Transactional FunReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid FunReviewRequest request, @AuthenticationPrincipal User author) {
  requireCoupleReviewer(author);
  WhyFunVenue venue = findVenue(id);
  WhyFunVenueReview review = reviews.findByVenueIdAndAuthorId(id, author.id).orElseGet(() -> {
   WhyFunVenueReview value = new WhyFunVenueReview(); value.venue = venue; value.author = author; value.createdAt = Instant.now(); return value;
  });
  review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.updatedAt = Instant.now();
  return review(reviews.save(review));
 }

 private WhyFunVenue findVenue(Long id) { return venues.findDetailedById(id).orElseThrow(() -> notFound("Lugar")); }
 private WhyFunCategory findCategory(Long id) { return categories.findDetailedById(id).orElseThrow(() -> notFound("Categoría")); }

 private FunVenueDto venue(WhyFunVenue value) {
  List<WhyFunVenuePhoto> venuePhotos = photos.findByVenueIdOrderByIdAsc(value.id);
  List<FunReviewDto> venueReviews = reviews.summariesByVenueId(value.id).stream().map(WhyFunApi::review).toList();
  return venue(value, venuePhotos.isEmpty() ? null : photo(venuePhotos.getFirst()), schedules(value), venuePhotos.stream().map(WhyFunApi::photo).toList(), venueReviews);
 }

 private static FunVenueDto venue(WhyFunVenue value, FunPhotoDto cover, List<FunScheduleDto> schedules, List<FunPhotoDto> venuePhotos, List<FunReviewDto> venueReviews) {
  double rating = venueReviews.stream().mapToInt(FunReviewDto::rating).average().orElse(0);
  return new FunVenueDto(value.id, value.name, value.address, categorySummary(value.category), categorySummary(value.subcategory), value.createdBy.username, round(rating), venueReviews.size(), cover, schedules, venuePhotos, venueReviews, value.createdAt, value.updatedAt);
 }

 private Map<Long, FunPhotoDto> covers(List<Long> ids) {
  if (ids.isEmpty()) return Map.of();
  Map<Long, FunPhotoDto> result = new HashMap<>();
  for (WhyFunVenuePhoto photo : photos.findByVenueIdInOrderByVenueIdAscIdAsc(ids)) result.putIfAbsent(photo.venue.id, photo(photo));
  return result;
 }

 private Map<Long, List<FunReviewDto>> reviewMap(List<Long> ids) {
  if (ids.isEmpty()) return Map.of();
  return reviews.summariesByVenueIdIn(ids).stream().collect(Collectors.groupingBy(WhyFunReviewSummary::getVenueId, Collectors.mapping(WhyFunApi::review, Collectors.toList())));
 }

 private static List<FunScheduleDto> schedules(WhyFunVenue venue) {
  return venue.schedules.stream().sorted(Comparator.comparing((WhyFunVenueSchedule schedule) -> schedule.dayOfWeek.getValue()).thenComparing(schedule -> schedule.opensAt)).map(schedule -> new FunScheduleDto(schedule.dayOfWeek, schedule.opensAt, schedule.closesAt)).toList();
 }

 private void apply(WhyFunCategory category, FunCategoryRequest request, WhyFunCategory current) {
  WhyFunCategory parent = request.parentId() == null ? null : findCategory(request.parentId());
  if (parent != null && parent.parent != null) throw badRequest("Las subcategorías solo pueden tener una categoría principal");
  if (parent != null && parent.id.equals(category.id)) throw badRequest("Una categoría no puede ser su propia subcategoría");
  String slug = slugFor(request.name());
  if (slug.isBlank()) throw badRequest("El nombre debe incluir letras o números");
  Optional<WhyFunCategory> duplicate = parent == null ? categories.findByParentIsNullAndSlug(slug) : categories.findByParentIdAndSlug(parent.id, slug);
  if (duplicate.isPresent() && (current == null || !duplicate.get().id.equals(current.id))) throw conflict("Ya existe una categoría con ese nombre");
  category.parent = parent; category.name = request.name().trim(); category.slug = slug; category.icon = request.icon().trim(); category.active = request.active();
 }

 private void apply(WhyFunVenue venue, FunVenueRequest request) {
  WhyFunCategory category = findCategory(request.categoryId());
  WhyFunCategory subcategory = findCategory(request.subcategoryId());
  if (!category.active || category.parent != null) throw badRequest("Elegí una categoría principal activa");
  if (!subcategory.active || subcategory.parent == null || !subcategory.parent.id.equals(category.id)) throw badRequest("Elegí una subcategoría activa de la categoría seleccionada");
  validateSchedules(request.schedules());
  venue.name = request.name().trim(); venue.address = request.address().trim(); venue.category = category; venue.subcategory = subcategory; venue.schedules.clear();
  for (FunScheduleRequest source : request.schedules()) {
   WhyFunVenueSchedule schedule = new WhyFunVenueSchedule(); schedule.venue = venue; schedule.dayOfWeek = source.day(); schedule.opensAt = source.opensAt(); schedule.closesAt = source.closesAt(); venue.schedules.add(schedule);
  }
 }

 static void validateSchedules(List<FunScheduleRequest> values) {
  Map<DayOfWeek, List<FunScheduleRequest>> byDay = values.stream().collect(Collectors.groupingBy(FunScheduleRequest::day));
  for (List<FunScheduleRequest> schedules : byDay.values()) {
   List<FunScheduleRequest> sorted = schedules.stream().sorted(Comparator.comparing(FunScheduleRequest::opensAt)).toList();
   for (int index = 0; index < sorted.size(); index++) {
    FunScheduleRequest current = sorted.get(index);
    if (current.opensAt().equals(current.closesAt())) throw badRequest("Cada horario debe tener horas de apertura y cierre distintas");
    if (index > 0 && closesAfter(sorted.get(index - 1)) > current.opensAt().toSecondOfDay()) throw badRequest("Los horarios de un mismo día no pueden superponerse");
   }
  }
 }

 private static int closesAfter(FunScheduleRequest schedule) {
  int opensAt = schedule.opensAt().toSecondOfDay();
  int closesAt = schedule.closesAt().toSecondOfDay();
  return closesAt > opensAt ? closesAt : closesAt + 86_400;
 }

 private static FunCategoryDto category(WhyFunCategory value) { return new FunCategoryDto(value.id, value.parent == null ? null : value.parent.id, value.name, value.slug, value.icon, value.active); }
 private static FunCategoryDto categorySummary(WhyFunCategory value) { return new FunCategoryDto(value.id, null, value.name, value.slug, value.icon, value.active); }
 private static FunPhotoDto photo(WhyFunVenuePhoto value) { return new FunPhotoDto(value.id, "/why-fun/photos/" + value.id, "/why-fun/photos/" + value.id + "?thumbnail=true", value.width, value.height); }
 private static FunReviewDto review(WhyFunVenueReview value) { return new FunReviewDto(value.id, value.author.username, value.rating, value.comment, value.updatedAt); }
 private static FunReviewDto review(WhyFunReviewSummary value) { return new FunReviewDto(value.getId(), value.getAuthor(), value.getRating(), value.getComment(), value.getUpdatedAt()); }
 private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static String slugFor(String value) { return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
 private static WhyFunVenue owned(WhyFunVenue venue, User user) { if (!venue.createdBy.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return venue; }
 private static WhyFunVenuePhoto owned(WhyFunVenuePhoto photo, User user) { owned(photo.venue, user); return photo; }
 private static void requireCoupleReviewer(User user) { if (!Set.of("tomas", "avril").contains(user.username.toLowerCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Tomás y Avril pueden reseñar lugares"); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
