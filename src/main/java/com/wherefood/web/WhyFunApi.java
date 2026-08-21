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
record FunPlanRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 250) String address, LocalDate scheduledAt, @NotNull Long categoryId, @NotNull Long subcategoryId, List<@Valid ActivityScheduleRequest> schedules) {}
record FunPhotoDto(Long id, String url, String thumbnailUrl, int width, int height) {}
record FunReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record FunReviewDto(Long id, String author, short rating, String comment, Instant updatedAt) {}
record FunPlanDto(Long id, String name, String address, LocalDate scheduledAt, FunCategoryDto category, FunCategoryDto subcategory, String author, double rating, int reviewCount, FunPhotoDto coverPhoto, List<FunPhotoDto> photos, List<FunReviewDto> reviews, List<ActivityScheduleDto> schedules, Instant createdAt, Instant updatedAt) {}

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

 @GetMapping("/categories") List<FunCategoryDto> activeCategories() { return categories.findAllByOrderByParentIdAscNameAsc().stream().filter(category -> category.active && (category.parent == null || category.parent.active)).map(WhyFunApi::category).toList(); }
 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<FunCategoryDto> allCategories() { return categories.findAllByOrderByParentIdAscNameAsc().stream().map(WhyFunApi::category).toList(); }
 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") @Transactional FunCategoryDto addCategory(@RequestBody @Valid FunCategoryRequest request) { WhyFunCategory category = new WhyFunCategory(); apply(category, request, null); category.createdAt = category.updatedAt = Instant.now(); return category(categories.save(category)); }
 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional FunCategoryDto updateCategory(@PathVariable Long id, @RequestBody @Valid FunCategoryRequest request) { WhyFunCategory category = findCategory(id); Long currentParentId = category.parent == null ? null : category.parent.id; if (!Objects.equals(currentParentId, request.parentId()) && (categories.existsByParentId(id) || venues.countBySubcategoryId(id) > 0)) throw conflict("No podés cambiar la jerarquía de una categoría que ya tiene subcategorías o planes"); apply(category, request, category); category.updatedAt = Instant.now(); return category(categories.save(category)); }
 @DeleteMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteCategory(@PathVariable Long id) { WhyFunCategory category = findCategory(id); if (categories.existsByParentId(id)) throw conflict("No podés borrar una categoría con subcategorías"); if (venues.existsByCategoryIdOrSubcategoryId(id, id)) throw conflict("No podés borrar una categoría usada por planes"); categories.delete(category); }

 @GetMapping("/plans") Slice<FunPlanDto> listPlans(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long subcategoryId, @RequestParam(required = false) String timeline, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  LocalDate now = RosarioClock.today();
  Comparator<WhyFunVenue> order = "UPCOMING".equals(timeline) ? Comparator.comparing((WhyFunVenue value) -> value.scheduledAt, Comparator.nullsLast(Comparator.naturalOrder())) : "PAST".equals(timeline) ? Comparator.comparing((WhyFunVenue value) -> value.scheduledAt, Comparator.nullsLast(Comparator.reverseOrder())) : Comparator.comparing((WhyFunVenue value) -> value.createdAt).reversed();
  List<WhyFunVenue> values = venues.findAll().stream().filter(value -> categoryId == null || value.category.id.equals(categoryId)).filter(value -> subcategoryId == null || value.subcategory.id.equals(subcategoryId)).filter(value -> matchesTimeline(value, timeline, now)).sorted(order.thenComparing(value -> value.id, Comparator.reverseOrder())).toList();
  int offset = cursor == null ? 0 : Math.max(0, cursor.intValue());
  List<WhyFunVenue> page = values.stream().skip(offset).limit(limit).toList();
  List<Long> ids = page.stream().map(value -> value.id).toList();
  Map<Long, List<FunReviewDto>> reviewMap = reviewMap(ids);
  Map<Long, FunPhotoDto> coverMap = covers(page);
  Long next = offset + page.size() < values.size() ? (long) offset + page.size() : null;
  return new Slice<>(page.stream().map(value -> plan(value, coverMap.get(value.id), List.of(), reviewMap.getOrDefault(value.id, List.of()))).toList(), next);
 }
 @GetMapping("/plans/{id}") FunPlanDto getPlan(@PathVariable Long id) { return plan(findPlan(id)); }
  @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED) @Transactional FunPlanDto addPlan(@RequestBody @Valid FunPlanRequest request, @AuthenticationPrincipal User author) { WhyFunVenue plan = new WhyFunVenue(); plan.createdBy = plan.updatedBy = author; apply(plan, request); plan.createdAt = plan.updatedAt = Instant.now(); return plan(venues.save(plan)); }
  @PutMapping("/plans/{id}") @Transactional FunPlanDto updatePlan(@PathVariable Long id, @RequestBody @Valid FunPlanRequest request, @AuthenticationPrincipal User author) { WhyFunVenue plan = findPlan(id); apply(plan, request); plan.updatedBy = author; plan.updatedAt = Instant.now(); return plan(venues.save(plan)); }
  @DeleteMapping("/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePlan(@PathVariable Long id, @AuthenticationPrincipal User author) { venues.delete(findPlan(id)); }
  @PostMapping(value = "/plans/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional FunPlanDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException { WhyFunVenue plan = findPlan(id); if (photos.countByVenueId(id) >= MAX_PHOTOS) throw conflict("Cada plan admite hasta " + MAX_PHOTOS + " fotos"); WhyFunVenuePhoto photo = photos.save(storage.store(plan, file)); if (plan.coverPhotoId == null) { plan.coverPhotoId = photo.id; plan.updatedBy = author; plan.updatedAt = Instant.now(); venues.save(plan); } return plan(plan); }
  @PutMapping("/plans/{id}/cover/{photoId}") @Transactional FunPlanDto setCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) { WhyFunVenue plan = findPlan(id); WhyFunVenuePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); if (!photo.venue.id.equals(plan.id)) throw badRequest("La foto no pertenece a este plan"); plan.coverPhotoId = photo.id; plan.updatedBy = author; plan.updatedAt = Instant.now(); return plan(venues.save(plan)); }
  @DeleteMapping("/photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) { WhyFunVenuePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); WhyFunVenue plan = photo.venue; if (photo.id.equals(plan.coverPhotoId)) { plan.coverPhotoId = null; plan.updatedBy = author; plan.updatedAt = Instant.now(); venues.save(plan); } photos.delete(photo); }
 @GetMapping(value = "/photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) { WhyFunVenuePhoto photo = photos.findById(photoId).orElseThrow(() -> notFound("Foto")); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64)); }
  @PutMapping("/plans/{id}/review") @Transactional FunReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid FunReviewRequest request, @AuthenticationPrincipal User author) { WhyFunVenue plan = findPlan(id); WhyFunVenueReview review = reviews.findByVenueIdAndAuthorId(id, author.id).orElseGet(() -> { WhyFunVenueReview value = new WhyFunVenueReview(); value.venue = plan; value.author = author; value.createdAt = Instant.now(); return value; }); review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.updatedAt = Instant.now(); return review(reviews.save(review)); }

 private WhyFunVenue findPlan(Long id) { return venues.findDetailedById(id).orElseThrow(() -> notFound("Plan")); }
 private WhyFunCategory findCategory(Long id) { return categories.findDetailedById(id).orElseThrow(() -> notFound("Categoría")); }
 private FunPlanDto plan(WhyFunVenue value) { List<WhyFunVenuePhoto> planPhotos = photos.findByVenueIdOrderByIdAsc(value.id); List<FunReviewDto> planReviews = reviews.summariesByVenueId(value.id).stream().map(WhyFunApi::review).toList(); return plan(value, cover(value, planPhotos), planPhotos.stream().map(WhyFunApi::photo).toList(), planReviews); }
  private static FunPlanDto plan(WhyFunVenue value, FunPhotoDto cover, List<FunPhotoDto> planPhotos, List<FunReviewDto> planReviews) { double rating = planReviews.stream().mapToInt(FunReviewDto::rating).average().orElse(0); List<ActivityScheduleDto> schedules = value.schedules.stream().sorted(Comparator.comparing((WhyFunVenueSchedule schedule) -> schedule.dayOfWeek).thenComparing(schedule -> schedule.opensAt)).map(schedule -> new ActivityScheduleDto(schedule.dayOfWeek, schedule.opensAt, schedule.closesAt)).toList(); return new FunPlanDto(value.id, value.name, value.address, value.scheduledAt, categorySummary(value.category), categorySummary(value.subcategory), value.createdBy.username, round(rating), planReviews.size(), cover, planPhotos, planReviews, schedules, value.createdAt, value.updatedAt); }
 private Map<Long, FunPhotoDto> covers(List<WhyFunVenue> plans) { if (plans.isEmpty()) return Map.of(); Map<Long, List<WhyFunVenuePhoto>> photosByPlan = photos.findByVenueIdInOrderByVenueIdAscIdAsc(plans.stream().map(value -> value.id).toList()).stream().collect(Collectors.groupingBy(value -> value.venue.id)); return plans.stream().collect(Collectors.toMap(value -> value.id, value -> cover(value, photosByPlan.getOrDefault(value.id, List.of())), (first, ignored) -> first)); }
 private static FunPhotoDto cover(WhyFunVenue plan, List<WhyFunVenuePhoto> values) { WhyFunVenuePhoto selected = values.stream().filter(value -> value.id.equals(plan.coverPhotoId)).findFirst().orElse(values.isEmpty() ? null : values.getFirst()); return selected == null ? null : photo(selected); }
 private Map<Long, List<FunReviewDto>> reviewMap(List<Long> ids) { if (ids.isEmpty()) return Map.of(); return reviews.summariesByVenueIdIn(ids).stream().collect(Collectors.groupingBy(WhyFunReviewSummary::getVenueId, Collectors.mapping(WhyFunApi::review, Collectors.toList()))); }
 private void apply(WhyFunCategory category, FunCategoryRequest request, WhyFunCategory current) { WhyFunCategory parent = request.parentId() == null ? null : findCategory(request.parentId()); if (parent != null && parent.parent != null) throw badRequest("Las subcategorías solo pueden tener una categoría principal"); if (parent != null && parent.id.equals(category.id)) throw badRequest("Una categoría no puede ser su propia subcategoría"); String slug = slugFor(request.name()); if (slug.isBlank()) throw badRequest("El nombre debe incluir letras o números"); Optional<WhyFunCategory> duplicate = parent == null ? categories.findByParentIsNullAndSlug(slug) : categories.findByParentIdAndSlug(parent.id, slug); if (duplicate.isPresent() && (current == null || !duplicate.get().id.equals(current.id))) throw conflict("Ya existe una categoría con ese nombre"); category.parent = parent; category.name = request.name().trim(); category.slug = slug; category.icon = request.icon().trim(); category.active = request.active(); }
  private void apply(WhyFunVenue plan, FunPlanRequest request) { WhyFunCategory category = findCategory(request.categoryId()); WhyFunCategory subcategory = findCategory(request.subcategoryId()); if (!category.active || category.parent != null) throw badRequest("Elegí una categoría principal activa"); if (!subcategory.active || subcategory.parent == null || !subcategory.parent.id.equals(category.id)) throw badRequest("Elegí una subcategoría activa de la categoría seleccionada"); plan.name = request.name().trim(); plan.address = request.address().trim(); plan.scheduledAt = request.scheduledAt(); plan.category = category; plan.subcategory = subcategory; plan.schedules.clear(); if (plan.id != null) venues.flush(); if (request.schedules() != null) for (ActivityScheduleRequest source : request.schedules()) { if (source.opensAt().equals(source.closesAt())) throw badRequest("El horario de apertura y cierre debe ser distinto"); WhyFunVenueSchedule schedule = new WhyFunVenueSchedule(); schedule.venue = plan; schedule.dayOfWeek = source.dayOfWeek(); schedule.opensAt = source.opensAt(); schedule.closesAt = source.closesAt(); plan.schedules.add(schedule); } }
 private static boolean matchesTimeline(WhyFunVenue value, String timeline, LocalDate now) { return switch (timeline == null ? "ALL" : timeline) { case "UPCOMING" -> value.scheduledAt != null && !value.scheduledAt.isBefore(now); case "PAST" -> value.scheduledAt != null && value.scheduledAt.isBefore(now); case "UNSCHEDULED" -> value.scheduledAt == null; default -> true; }; }
 private static FunCategoryDto category(WhyFunCategory value) { return new FunCategoryDto(value.id, value.parent == null ? null : value.parent.id, value.name, value.slug, value.icon, value.active); }
 private static FunCategoryDto categorySummary(WhyFunCategory value) { return new FunCategoryDto(value.id, null, value.name, value.slug, value.icon, value.active); }
 private static FunPhotoDto photo(WhyFunVenuePhoto value) { return new FunPhotoDto(value.id, "/why-fun/photos/" + value.id, "/why-fun/photos/" + value.id + "?thumbnail=true", value.width, value.height); }
 private static FunReviewDto review(WhyFunVenueReview value) { return new FunReviewDto(value.id, value.author.username, value.rating, value.comment, value.updatedAt); }
 private static FunReviewDto review(WhyFunReviewSummary value) { return new FunReviewDto(value.getId(), value.getAuthor(), value.getRating(), value.getComment(), value.getUpdatedAt()); }
 private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static String slugFor(String value) { return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
