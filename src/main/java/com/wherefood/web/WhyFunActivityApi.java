package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
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

record ActivityScheduleRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
record ActivityScheduleDto(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
record ActivityRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 250) String address, @NotNull @Positive Long categoryId, @NotNull @Positive Long subcategoryId, @Size(max = 7) List<@Valid ActivityScheduleRequest> schedules) {}
record ActivityProfilePhotoDto(Long id, String url, String thumbnailUrl, int width, int height, Instant createdAt) {}
record ActivityDto(Long id, String name, String address, FunCategoryDto category, FunCategoryDto subcategory, List<ActivityScheduleDto> schedules, ActivityProfilePhotoDto profilePhoto, Double rating, long visitCount, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {}
record ActivityVisitRequest(@NotNull LocalDate scheduledAt) {}
record ActivityPhotoDto(Long id, String url, String thumbnailUrl, int width, int height, int position, String createdBy, Instant createdAt) {}
record ActivityReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record ActivityReviewDto(Long id, String author, String updatedBy, short rating, String comment, Instant createdAt, Instant updatedAt) {}
record ActivityVisitDto(Long id, ActivityDto activity, LocalDate scheduledAt, String createdBy, String updatedBy, ActivityPhotoDto coverPhoto, List<ActivityPhotoDto> photos, List<ActivityReviewDto> reviews, Instant createdAt, Instant updatedAt) {}

/**
 * Active WhyFun contract: /why-fun/activities are reusable venues and
 * /why-fun/activity-visits are individual scheduled experiences.
 */
@RestController
@RequestMapping("/api/why-fun")
public class WhyFunActivityApi {
 private static final int MAX_PHOTOS = 4;
 private final WhyFunCategories categories;
 private final WhyFunVenues activities;
 private final WhyFunVenuePhotos activityPhotos;
 private final WhyFunVisits visits;
 private final WhyFunVisitPhotos photos;
 private final WhyFunVisitReviews reviews;
 private final PhotoStorage storage;
 private final WhyFunVisitReviewService reviewService;
 private final WhyFunActivityService activityService;
 private final WhyFunVisitService visitService;
 private final WhyFunMediaService mediaService;

 public WhyFunActivityApi(WhyFunCategories categories, WhyFunVenues activities, WhyFunVenuePhotos activityPhotos, WhyFunVisits visits, WhyFunVisitPhotos photos, WhyFunVisitReviews reviews, PhotoStorage storage) {
  this(categories, activities, activityPhotos, visits, photos, reviews, storage,
          new WhyFunVisitReviewService(reviews, visits, new CoupleAuthorizationService(null)),
          new WhyFunActivityService(categories, activities, new CoupleAuthorizationService(null)),
          new WhyFunVisitService(activities, visits, new CoupleAuthorizationService(null)));
 }

 public WhyFunActivityApi(WhyFunCategories categories, WhyFunVenues activities, WhyFunVenuePhotos activityPhotos, WhyFunVisits visits, WhyFunVisitPhotos photos, WhyFunVisitReviews reviews, PhotoStorage storage, WhyFunVisitReviewService reviewService) {
  this(categories, activities, activityPhotos, visits, photos, reviews, storage, reviewService,
          new WhyFunActivityService(categories, activities, new CoupleAuthorizationService(null)),
          new WhyFunVisitService(activities, visits, new CoupleAuthorizationService(null)));
 }

 public WhyFunActivityApi(WhyFunCategories categories, WhyFunVenues activities,
         WhyFunVenuePhotos activityPhotos, WhyFunVisits visits, WhyFunVisitPhotos photos,
         WhyFunVisitReviews reviews, PhotoStorage storage, WhyFunVisitReviewService reviewService,
         WhyFunActivityService activityService, WhyFunVisitService visitService) {
  this(categories, activities, activityPhotos, visits, photos, reviews, storage, reviewService,
          activityService, visitService, new WhyFunMediaService(activities, activityPhotos, visits,
                  photos, storage, new CoupleAuthorizationService(null)));
 }

 @org.springframework.beans.factory.annotation.Autowired
 public WhyFunActivityApi(WhyFunCategories categories, WhyFunVenues activities,
         WhyFunVenuePhotos activityPhotos, WhyFunVisits visits, WhyFunVisitPhotos photos,
         WhyFunVisitReviews reviews, PhotoStorage storage, WhyFunVisitReviewService reviewService,
         WhyFunActivityService activityService, WhyFunVisitService visitService,
         WhyFunMediaService mediaService) {
  this.categories = categories; this.activities = activities; this.activityPhotos = activityPhotos;
  this.visits = visits; this.photos = photos; this.reviews = reviews; this.storage = storage;
  this.reviewService = reviewService; this.activityService = activityService; this.visitService = visitService; this.mediaService = mediaService;
 }

  @GetMapping("/activities") @Transactional(readOnly = true) Slice<ActivityDto> listActivities(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long subcategoryId, @RequestParam(required = false) String search, @RequestParam(required = false) Boolean visited, @RequestParam(required = false) String sort, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int size) {
    int limit = Math.max(1, Math.min(size, 30));
    String normalizedSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
    List<WhyFunVenue> values = activities.findAllByCoupleId(CoupleContext.current()).stream().filter(value -> categoryId == null || value.category.id.equals(categoryId)).filter(value -> subcategoryId == null || value.subcategory.id.equals(subcategoryId)).filter(value -> normalizedSearch == null || contains(value.name, normalizedSearch) || contains(value.address, normalizedSearch) || contains(value.category.name, normalizedSearch) || contains(value.subcategory.name, normalizedSearch)).toList();
    Map<Long, Double> ratings = activityRatings(values.stream().map(value -> value.id).toList());
    Map<Long, Long> visitCounts = activityVisitCounts(values.stream().map(value -> value.id).toList());
    List<WhyFunVenue> candidates = values.stream().filter(value -> visited == null || visited == (visitCounts.getOrDefault(value.id, 0L) > 0)).toList();
     Comparator<WhyFunVenue> dateDescending = Comparator.comparing((WhyFunVenue value) -> value.updatedAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(value -> value.createdAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(value -> value.id, Comparator.reverseOrder());
     Comparator<WhyFunVenue> ordering = switch (sort == null ? "date-desc" : sort.trim().toLowerCase(Locale.ROOT)) {
     case "name" -> Comparator.comparing((WhyFunVenue value) -> value.name, String.CASE_INSENSITIVE_ORDER).thenComparing(value -> value.id);
     case "date", "date-desc" -> dateDescending;
      case "date-asc" -> Comparator.comparing((WhyFunVenue value) -> value.updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(value -> value.createdAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(value -> value.id, Comparator.reverseOrder());
     case "rating", "rating-desc" -> Comparator.comparing((WhyFunVenue value) -> ratings.get(value.id), Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(dateDescending);
     case "rating-asc" -> Comparator.comparing((WhyFunVenue value) -> ratings.get(value.id), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(dateDescending);
     default -> throw badRequest("Orden inválido");
    };
    long offset = cursor == null ? 0 : Math.max(0, cursor);
    List<WhyFunVenue> result = candidates.stream().sorted(ordering).skip(offset).limit(limit + 1L).toList();
    Long next = result.size() > limit ? offset + limit : null;
    List<WhyFunVenue> page = result.stream().limit(limit).toList();
    Map<Long, PhotoMetadata> profilesById = profilePhotos(page);
    return new Slice<>(page.stream().map(value -> activity(value, ratings.get(value.id), visitCounts.getOrDefault(value.id, 0L), value.coverPhotoId == null ? null : profilesById.get(value.coverPhotoId))).toList(), next);
  }
 @GetMapping("/activities/{id}") @Transactional(readOnly = true) ActivityDto getActivity(@PathVariable Long id) { return activity(findActivity(id)); }
 @PostMapping("/activities") @ResponseStatus(HttpStatus.CREATED) ActivityDto addActivity(@RequestBody @Valid ActivityRequest request, @AuthenticationPrincipal User author) {
  return activity(activityService.create(request, author));
 }
  @PutMapping("/activities/{id}") ActivityDto updateActivity(@PathVariable Long id, @RequestBody @Valid ActivityRequest request, @AuthenticationPrincipal User author) {
   return activity(activityService.update(id, request, author));
  }
  @DeleteMapping("/activities/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteActivity(@PathVariable Long id, @AuthenticationPrincipal User author) { activityService.delete(id, author); }
  @GetMapping(value = "/activities/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> activityPhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   WhyFunVenue activity = findActivity(id); WhyFunVenuePhoto photo = profilePhoto(activity).orElseThrow(() -> notFound("Foto"));
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping(value = "/activities/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) ActivityDto uploadActivityPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   return activity(mediaService.uploadVenuePhoto(id, file, author));
  }

 @GetMapping("/activities/{id}/visits") @Transactional(readOnly = true) List<ActivityVisitDto> listVisits(@PathVariable Long id) { WhyFunVenue activity = findActivity(id); ActivityDto dto = activity(activity); return visits.findByVenueIdAndCoupleIdOrderByScheduledAtDescIdDesc(id, CoupleContext.current()).stream().map(visit -> visit(visit, dto)).toList(); }
  @PostMapping("/activities/{id}/visits") @ResponseStatus(HttpStatus.CREATED) ActivityVisitDto addVisit(@PathVariable Long id, @RequestBody @Valid ActivityVisitRequest request, @AuthenticationPrincipal User author) {
   return visit(visitService.create(id, request, author));
 }
 @GetMapping("/activity-visits/{id}") @Transactional(readOnly = true) ActivityVisitDto getVisit(@PathVariable Long id) { return visit(findVisit(id)); }
 @PutMapping("/activity-visits/{id}") ActivityVisitDto updateVisit(@PathVariable Long id, @RequestBody @Valid ActivityVisitRequest request, @AuthenticationPrincipal User author) {
   return visit(visitService.update(id, request, author));
 }
  @DeleteMapping("/activity-visits/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteVisit(@PathVariable Long id, @AuthenticationPrincipal User author) { visitService.delete(id, author); }

 @PostMapping(value = "/activity-visits/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) ActivityVisitDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
  return visit(mediaService.uploadVisitPhoto(id, file, author));
 }
 @PutMapping("/activity-visits/{id}/cover/{photoId}") ActivityVisitDto setCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
  return visit(mediaService.setVisitCover(id, photoId, author));
 }
 @DeleteMapping("/activity-visit-photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
  mediaService.deleteVisitPhoto(photoId, author);
 }
 @GetMapping(value = "/activity-visit-photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
   WhyFunVisitPhoto photo = photos.findByIdAndCoupleId(photoId, CoupleContext.current()).orElseThrow(() -> notFound("Foto")); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PostMapping("/activity-visits/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) ActivityReviewDto addReview(@PathVariable Long id, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.create(id, request, author));
 }
 @PutMapping("/activity-visits/{id}/reviews/me") ActivityReviewDto saveOwnReview(@PathVariable Long id, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.saveOwn(id, request, author));
 }
 @PutMapping("/activity-visit-reviews/{reviewId}") ActivityReviewDto updateReview(@PathVariable Long reviewId, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.update(reviewId, request, author));
 }
 @DeleteMapping("/activity-visit-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long reviewId, @AuthenticationPrincipal User author) { reviewService.delete(reviewId, author); }

 private WhyFunVenue findActivity(Long id) { return activities.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Actividad")); }
 private WhyFunVisit findVisit(Long id) { return visits.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Visita")); }
  private ActivityVisitDto visit(WhyFunVisit value) { return visit(value, activity(value.venue)); }
 private ActivityVisitDto visit(WhyFunVisit value, ActivityDto activity) {
  List<WhyFunVisitPhoto> visitPhotos = photos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(value.id, CoupleContext.current()); List<ActivityPhotoDto> resultPhotos = visitPhotos.stream().map(WhyFunActivityApi::photo).toList();
  ActivityPhotoDto cover = resultPhotos.stream().filter(photo -> photo.id().equals(value.coverPhotoId)).findFirst().orElse(resultPhotos.isEmpty() ? null : resultPhotos.getFirst());
  List<WhyFunVisitReview> reviewValues = reviews.findByVisitIdAndCoupleIdOrderByAuthorUsername(value.id, CoupleContext.current());
  Map<Long, String> reviewAuthors = reviews.authorsByVisitIdAndCoupleId(value.id, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
  return new ActivityVisitDto(value.id, activity, value.scheduledAt, value.createdBy.username, value.updatedBy.username, cover, resultPhotos, reviewValues.stream().map(review -> review(review, reviewAuthors.get(review.id))).toList(), value.createdAt, value.updatedAt);
 }
   private ActivityDto activity(WhyFunVenue value) { return activity(value, activityRatings(List.of(value.id)).get(value.id), activityVisitCounts(List.of(value.id)).getOrDefault(value.id, 0L), value.coverPhotoId == null ? null : profilePhotos(List.of(value)).get(value.coverPhotoId)); }
   private ActivityDto activity(WhyFunVenue value, Double rating, long visitCount, PhotoMetadata profile) { return new ActivityDto(value.id, value.name, value.address, category(value.category), category(value.subcategory), value.schedules.stream().sorted(Comparator.comparing((WhyFunVenueSchedule schedule) -> schedule.dayOfWeek).thenComparing(schedule -> schedule.opensAt)).map(schedule -> new ActivityScheduleDto(schedule.dayOfWeek, schedule.opensAt, schedule.closesAt)).toList(), profile == null ? null : profilePhoto(value.id, profile), rating, visitCount, value.createdBy.username, value.updatedBy.username, value.createdAt, value.updatedAt); }
  private Map<Long, Double> activityRatings(Collection<Long> activityIds) { if (activityIds.isEmpty() || reviews == null) return Map.of(); return reviews.ratingsByActivityIdInAndCoupleId(activityIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ActivityRating::getActivityId, ActivityRating::getRating)); }
   private Map<Long, Long> activityVisitCounts(Collection<Long> activityIds) { if (activityIds.isEmpty() || visits == null) return Map.of(); return visits.countsByActivityIdInAndCoupleId(activityIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ActivityVisitCount::getActivityId, ActivityVisitCount::getVisitCount)); }
   private Map<Long, PhotoMetadata> profilePhotos(Collection<WhyFunVenue> values) {
    if (values.isEmpty() || activityPhotos == null) return Map.of();
    List<Long> photoIds = values.stream().map(value -> value.coverPhotoId).filter(Objects::nonNull).toList();
    if (photoIds.isEmpty()) return Map.of();
    return activityPhotos.metadataByIdInAndCoupleId(photoIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(PhotoMetadata::getId, photo -> photo));
   }
   private Optional<WhyFunVenuePhoto> profilePhoto(WhyFunVenue value) { return value.coverPhotoId == null ? Optional.empty() : activityPhotos.findByIdAndVenueIdAndCoupleId(value.coverPhotoId, value.id, CoupleContext.current()); }
   private static ActivityProfilePhotoDto profilePhoto(Long activityId, PhotoMetadata value) { return new ActivityProfilePhotoDto(value.getId(), "/why-fun/activities/" + activityId + "/photo?v=" + value.getId(), "/why-fun/activities/" + activityId + "/photo?thumbnail=true&v=" + value.getId(), value.getWidth(), value.getHeight(), value.getCreatedAt()); }
 private static FunCategoryDto category(WhyFunCategory value) { return new FunCategoryDto(value.id, value.parent == null ? null : value.parent.id, value.name, value.slug, value.icon, value.active); }
 private static ActivityPhotoDto photo(WhyFunVisitPhoto value) { return new ActivityPhotoDto(value.id, "/why-fun/activity-visit-photos/" + value.id, "/why-fun/activity-visit-photos/" + value.id + "?thumbnail=true", value.width, value.height, value.position, value.createdBy.username, value.createdAt); }
  private static ActivityReviewDto review(WhyFunVisitReview value) { return review(value, value.author.username); }
 private static ActivityReviewDto review(WhyFunVisitReview value, String author) { return new ActivityReviewDto(value.id, author, value.updatedBy.username, value.rating, value.comment, value.createdAt, value.updatedAt); }
  private static boolean contains(String value, String search) { return value != null && value.toLowerCase(Locale.ROOT).contains(search); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
