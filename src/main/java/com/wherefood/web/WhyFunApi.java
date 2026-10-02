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
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.config.CoupleContext;

record FunCategoryRequest(Long parentId, @NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String icon, boolean active) {}
record FunCategoryDto(Long id, Long parentId, String name, String slug, String icon, boolean active) {}
record FunPlanRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 250) String address, LocalDate scheduledAt, @NotNull @Positive Long categoryId, @NotNull @Positive Long subcategoryId, @Size(max = 7) List<@Valid ActivityScheduleRequest> schedules, @Positive Long zoneId) {
 FunPlanRequest(String name, String address, LocalDate scheduledAt, Long categoryId, Long subcategoryId, List<ActivityScheduleRequest> schedules) {
  this(name, address, scheduledAt, categoryId, subcategoryId, schedules, null);
 }
}
record FunPhotoDto(Long id, String url, String thumbnailUrl, int width, int height) {}
record FunReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record FunReviewDto(Long id, String author, short rating, String comment, Instant updatedAt) {}
record FunPlanDto(Long id, Long zoneId, String name, String address, LocalDate scheduledAt, FunCategoryDto category, FunCategoryDto subcategory, String author, double rating, int reviewCount, FunPhotoDto coverPhoto, List<FunPhotoDto> photos, List<FunReviewDto> reviews, List<ActivityScheduleDto> schedules, Instant createdAt, Instant updatedAt) {}

@RestController
@RequestMapping("/api/why-fun")
public class WhyFunApi {
 private static final int MAX_PHOTOS = 12;
 private final WhyFunCategories categories;
 private final WhyFunVenues venues;
 private final WhyFunVenuePhotos photos;
 private final WhyFunVenueReviews reviews;
 private final PhotoStorage storage;
 private final WhyFunPlanService planService;
 private final WhyFunMediaService mediaService;
 private final WhyFunCategoryAdminService categoryAdminService;

 public WhyFunApi(WhyFunCategories categories, WhyFunVenues venues, WhyFunVenuePhotos photos, WhyFunVenueReviews reviews, PhotoStorage storage) {
  this(categories, venues, photos, reviews, storage,
          new WhyFunPlanService(categories, venues, reviews, new CoupleAuthorizationService(null)),
          new WhyFunMediaService(venues, photos, null, null, storage,
                  new CoupleAuthorizationService(null)),
          new WhyFunCategoryAdminService(categories));
 }

 @org.springframework.beans.factory.annotation.Autowired
 public WhyFunApi(WhyFunCategories categories, WhyFunVenues venues, WhyFunVenuePhotos photos,
         WhyFunVenueReviews reviews, PhotoStorage storage, WhyFunPlanService planService,
         WhyFunMediaService mediaService, WhyFunCategoryAdminService categoryAdminService) {
  this.categories = categories; this.venues = venues; this.photos = photos; this.reviews = reviews; this.storage = storage;
  this.planService = planService; this.mediaService = mediaService; this.categoryAdminService = categoryAdminService;
 }

 @GetMapping("/categories") List<FunCategoryDto> activeCategories() { return categories.findAllByOrderByParentIdAscNameAsc().stream().filter(category -> category.active && (category.parent == null || category.parent.active)).map(WhyFunApi::category).toList(); }
 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<FunCategoryDto> allCategories() { return categories.findAllByOrderByParentIdAscNameAsc().stream().map(WhyFunApi::category).toList(); }
 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") FunCategoryDto addCategory(@RequestBody @Valid FunCategoryRequest request) { return category(categoryAdminService.create(request)); }
 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") FunCategoryDto updateCategory(@PathVariable Long id, @RequestBody @Valid FunCategoryRequest request) { return category(categoryAdminService.update(id, request)); }
 @DeleteMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCategory(@PathVariable Long id) { categoryAdminService.delete(id); }

 @GetMapping("/plans") Slice<FunPlanDto> listPlans(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long subcategoryId, @RequestParam(required = false) String timeline, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
  int limit = Math.max(1, Math.min(size, 30));
  LocalDate now = RosarioClock.today();
  long offset = cursor == null ? 0 : Math.max(0, cursor);
  if (offset > 1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
  List<Long> ids = zoneId == null
          ? venues.findPlanPageIdsByCoupleId(CoupleContext.current(), categoryId, subcategoryId,
                  timeline, now, limit + 1, offset)
          : venues.findPlanPageIdsByCoupleId(CoupleContext.current(), zoneId, categoryId, subcategoryId,
                  timeline, now, limit + 1, offset);
  Long next = ids.size() > limit ? offset + limit : null;
  List<Long> pageIds = ids.stream().limit(limit).toList();
  if (pageIds.isEmpty()) return new Slice<>(List.of(), next);
  Map<Long, WhyFunVenue> byId = venues.findPlansByIdInAndCoupleId(pageIds, CoupleContext.current()).stream()
          .collect(Collectors.toMap(value -> value.id, value -> value));
  List<WhyFunVenue> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
  Map<Long, List<FunReviewDto>> reviewMap = reviewMap(pageIds);
  Map<Long, FunPhotoDto> coverMap = covers(page);
  return new Slice<>(page.stream().map(value -> plan(value, coverMap.get(value.id), List.of(), reviewMap.getOrDefault(value.id, List.of()))).toList(), next);
 }
 Slice<FunPlanDto> listPlans(Long categoryId, Long subcategoryId, String timeline, Long cursor, int size) {
  return listPlans(null, categoryId, subcategoryId, timeline, cursor, size);
 }
 @GetMapping("/plans/{id}") FunPlanDto getPlan(@PathVariable Long id) { return plan(findPlan(id)); }
  @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED) FunPlanDto addPlan(@RequestBody @Valid FunPlanRequest request, @AuthenticationPrincipal User author) { return plan(planService.create(request, author)); }
  @PutMapping("/plans/{id}") FunPlanDto updatePlan(@PathVariable Long id, @RequestBody @Valid FunPlanRequest request, @AuthenticationPrincipal User author) { return plan(planService.update(id, request, author)); }
  @DeleteMapping("/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlan(@PathVariable Long id, @AuthenticationPrincipal User author) { planService.delete(id, author); }
  @PostMapping(value = "/plans/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) FunPlanDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException { return plan(mediaService.uploadPlanPhoto(id, file, author)); }
  @PutMapping("/plans/{id}/cover/{photoId}") FunPlanDto setCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) { return plan(mediaService.setVenueCover(id, photoId, author)); }
  @DeleteMapping("/photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) { mediaService.deleteVenuePhoto(photoId, author); }
 @GetMapping(value = "/photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) { WhyFunVenuePhoto photo = photos.findByIdAndCoupleId(photoId, CoupleContext.current()).orElseThrow(() -> notFound("Foto")); return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64)); }
  @PutMapping("/plans/{id}/review") FunReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid FunReviewRequest request, @AuthenticationPrincipal User author) { return review(planService.saveOwnReview(id, request, author)); }

 private WhyFunVenue findPlan(Long id) { return venues.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Plan")); }
 private FunPlanDto plan(WhyFunVenue value) { List<WhyFunVenuePhoto> planPhotos = photos.findByVenueIdAndCoupleIdOrderByIdAsc(value.id, CoupleContext.current()); List<FunReviewDto> planReviews = reviews.summariesByVenueIdAndCoupleId(value.id, CoupleContext.current()).stream().map(WhyFunApi::review).toList(); return plan(value, cover(value, planPhotos), planPhotos.stream().map(WhyFunApi::photo).toList(), planReviews); }
  private static FunPlanDto plan(WhyFunVenue value, FunPhotoDto cover, List<FunPhotoDto> planPhotos, List<FunReviewDto> planReviews) { double rating = planReviews.stream().mapToInt(FunReviewDto::rating).average().orElse(0); List<ActivityScheduleDto> schedules = value.schedules.stream().sorted(Comparator.comparing((WhyFunVenueSchedule schedule) -> schedule.dayOfWeek).thenComparing(schedule -> schedule.opensAt)).map(schedule -> new ActivityScheduleDto(schedule.dayOfWeek, schedule.opensAt, schedule.closesAt)).toList(); return new FunPlanDto(value.id, value.zoneId, value.name, value.address, value.scheduledAt, categorySummary(value.category), categorySummary(value.subcategory), value.createdBy.username, round(rating), planReviews.size(), cover, planPhotos, planReviews, schedules, value.createdAt, value.updatedAt); }
 private Map<Long, FunPhotoDto> covers(List<WhyFunVenue> plans) { if (plans.isEmpty()) return Map.of(); Map<Long, List<WhyFunVenuePhoto>> photosByPlan = photos.findByVenueIdInAndCoupleIdOrderByVenueIdAscIdAsc(plans.stream().map(value -> value.id).toList(), CoupleContext.current()).stream().collect(Collectors.groupingBy(value -> value.venue.id)); Map<Long, FunPhotoDto> result = new HashMap<>(); for (WhyFunVenue plan : plans) { FunPhotoDto cover = cover(plan, photosByPlan.getOrDefault(plan.id, List.of())); if (cover != null) result.put(plan.id, cover); } return result; }
 private static FunPhotoDto cover(WhyFunVenue plan, List<WhyFunVenuePhoto> values) { WhyFunVenuePhoto selected = values.stream().filter(value -> value.id.equals(plan.coverPhotoId)).findFirst().orElse(values.isEmpty() ? null : values.getFirst()); return selected == null ? null : photo(selected); }
 private Map<Long, List<FunReviewDto>> reviewMap(List<Long> ids) { if (ids.isEmpty()) return Map.of(); return reviews.summariesByVenueIdInAndCoupleId(ids, CoupleContext.current()).stream().collect(Collectors.groupingBy(WhyFunReviewSummary::getVenueId, Collectors.mapping(WhyFunApi::review, Collectors.toList()))); }
 private static boolean matchesTimeline(WhyFunVenue value, String timeline, LocalDate now) { return switch (timeline == null ? "ALL" : timeline) { case "UPCOMING" -> value.scheduledAt != null && !value.scheduledAt.isBefore(now); case "PAST" -> value.scheduledAt != null && value.scheduledAt.isBefore(now); case "UNSCHEDULED" -> value.scheduledAt == null; default -> true; }; }
 private static FunCategoryDto category(WhyFunCategory value) { return new FunCategoryDto(value.id, value.parent == null ? null : value.parent.id, value.name, value.slug, value.icon, value.active); }
 private static FunCategoryDto categorySummary(WhyFunCategory value) { return new FunCategoryDto(value.id, null, value.name, value.slug, value.icon, value.active); }
 private static FunPhotoDto photo(WhyFunVenuePhoto value) { return new FunPhotoDto(value.id, "/why-fun/photos/" + value.id, "/why-fun/photos/" + value.id + "?thumbnail=true", value.width, value.height); }
 private static FunReviewDto review(WhyFunVenueReview value) { return new FunReviewDto(value.id, value.author.username, value.rating, value.comment, value.updatedAt); }
 private static FunReviewDto review(WhyFunReviewSummary value) { return new FunReviewDto(value.getId(), value.getAuthor(), value.getRating(), value.getComment(), value.getUpdatedAt()); }
  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
