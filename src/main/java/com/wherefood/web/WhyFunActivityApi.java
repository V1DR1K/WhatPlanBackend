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

record ActivityScheduleRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
record ActivityScheduleDto(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
record ActivityRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 250) String address, @NotNull Long categoryId, @NotNull Long subcategoryId, List<@Valid ActivityScheduleRequest> schedules) {}
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

 public WhyFunActivityApi(WhyFunCategories categories, WhyFunVenues activities, WhyFunVenuePhotos activityPhotos, WhyFunVisits visits, WhyFunVisitPhotos photos, WhyFunVisitReviews reviews, PhotoStorage storage) {
  this.categories = categories; this.activities = activities; this.activityPhotos = activityPhotos; this.visits = visits; this.photos = photos; this.reviews = reviews; this.storage = storage;
 }

 @GetMapping("/activities") @Transactional(readOnly = true) List<ActivityDto> listActivities(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long subcategoryId) {
   List<WhyFunVenue> values = activities.findAll().stream().filter(value -> categoryId == null || value.category.id.equals(categoryId)).filter(value -> subcategoryId == null || value.subcategory.id.equals(subcategoryId)).toList();
   Map<Long, Double> ratings = activityRatings(values.stream().map(value -> value.id).toList());
   Map<Long, Long> visitCounts = activityVisitCounts(values.stream().map(value -> value.id).toList());
   return values.stream().sorted(Comparator.comparing((WhyFunVenue value) -> value.name, String.CASE_INSENSITIVE_ORDER).thenComparing(value -> value.id)).map(value -> activity(value, ratings.get(value.id), visitCounts.getOrDefault(value.id, 0L))).toList();
 }
 @GetMapping("/activities/{id}") @Transactional(readOnly = true) ActivityDto getActivity(@PathVariable Long id) { return activity(findActivity(id)); }
 @PostMapping("/activities") @ResponseStatus(HttpStatus.CREATED) @Transactional ActivityDto addActivity(@RequestBody @Valid ActivityRequest request, @AuthenticationPrincipal User author) {
  WhyFunVenue activity = new WhyFunVenue(); activity.createdBy = activity.updatedBy = author; activity.createdAt = activity.updatedAt = Instant.now(); apply(activity, request); return activity(activities.save(activity));
 }
  @PutMapping("/activities/{id}") @Transactional ActivityDto updateActivity(@PathVariable Long id, @RequestBody @Valid ActivityRequest request, @AuthenticationPrincipal User author) {
   WhyFunVenue activity = findActivity(id); apply(activity, request); activity.updatedBy = author; activity.updatedAt = Instant.now(); return activity(activities.save(activity));
  }
  @DeleteMapping("/activities/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteActivity(@PathVariable Long id) { activities.delete(findActivity(id)); }
  @GetMapping(value = "/activities/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> activityPhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   WhyFunVenue activity = findActivity(id); WhyFunVenuePhoto photo = profilePhoto(activity).orElseThrow(() -> notFound("Foto"));
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping(value = "/activities/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional ActivityDto uploadActivityPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   WhyFunVenue activity = findActivity(id); profilePhoto(activity).ifPresent(activityPhotos::delete); activityPhotos.flush();
   WhyFunVenuePhoto photo = activityPhotos.save(storage.store(activity, file)); activity.coverPhotoId = photo.id; activity.updatedBy = author; activity.updatedAt = Instant.now(); activities.save(activity); return activity(activity);
  }

 @GetMapping("/activities/{id}/visits") @Transactional(readOnly = true) List<ActivityVisitDto> listVisits(@PathVariable Long id) { WhyFunVenue activity = findActivity(id); ActivityDto dto = activity(activity); return visits.findByVenueIdOrderByScheduledAtDescIdDesc(id).stream().map(visit -> visit(visit, dto)).toList(); }
  @PostMapping("/activities/{id}/visits") @ResponseStatus(HttpStatus.CREATED) @Transactional ActivityVisitDto addVisit(@PathVariable Long id, @RequestBody @Valid ActivityVisitRequest request, @AuthenticationPrincipal User author) {
   WhyFunVenue activity = findActivity(id); WhyFunVisit visit = new WhyFunVisit(); visit.venue = activity; visit.scheduledAt = request.scheduledAt(); visit.createdBy = visit.updatedBy = author; visit.createdAt = visit.updatedAt = Instant.now(); touch(activity, author); return visit(visits.save(visit));
 }
 @GetMapping("/activity-visits/{id}") @Transactional(readOnly = true) ActivityVisitDto getVisit(@PathVariable Long id) { return visit(findVisit(id)); }
 @PutMapping("/activity-visits/{id}") @Transactional ActivityVisitDto updateVisit(@PathVariable Long id, @RequestBody @Valid ActivityVisitRequest request, @AuthenticationPrincipal User author) {
   WhyFunVisit visit = findVisit(id); visit.scheduledAt = request.scheduledAt(); visit.updatedBy = author; visit.updatedAt = Instant.now(); touch(visit.venue, author); return visit(visits.save(visit));
 }
  @DeleteMapping("/activity-visits/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteVisit(@PathVariable Long id, @AuthenticationPrincipal User author) { WhyFunVisit visit = findVisit(id); visits.delete(visit); touch(visit.venue, author); }

 @PostMapping(value = "/activity-visits/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional ActivityVisitDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
  WhyFunVisit visit = findVisit(id); List<WhyFunVisitPhoto> current = photos.findByVisitIdOrderByPositionAscIdAsc(id); if (current.size() >= MAX_PHOTOS) throw conflict("Cada visita admite hasta " + MAX_PHOTOS + " fotos");
  WhyFunVisitPhoto photo = photos.save(storage.store(visit, author, current.isEmpty() ? 0 : current.getLast().position + 1, file));
  if (visit.coverPhotoId == null) { visit.coverPhotoId = photo.id; visit.updatedBy = author; visit.updatedAt = Instant.now(); visits.save(visit); }
  return visit(visit);
 }
 @PutMapping("/activity-visits/{id}/cover/{photoId}") @Transactional ActivityVisitDto setCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
  WhyFunVisit visit = findVisit(id); WhyFunVisitPhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); if (!photo.visit.id.equals(visit.id)) throw badRequest("La foto no pertenece a esta visita");
  visit.coverPhotoId = photo.id; visit.updatedBy = author; visit.updatedAt = Instant.now(); return visit(visits.save(visit));
 }
 @DeleteMapping("/activity-visit-photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
  WhyFunVisitPhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); WhyFunVisit visit = photo.visit; boolean wasCover = photo.id.equals(visit.coverPhotoId); photos.delete(photo); photos.flush();
  if (wasCover) { visit.coverPhotoId = photos.findByVisitIdOrderByPositionAscIdAsc(visit.id).stream().findFirst().map(value -> value.id).orElse(null); visit.updatedBy = author; visit.updatedAt = Instant.now(); visits.save(visit); }
 }
 @GetMapping(value = "/activity-visit-photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
  WhyFunVisitPhoto photo = photos.findById(photoId).orElseThrow(() -> notFound("Foto")); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PostMapping("/activity-visits/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) @Transactional ActivityReviewDto addReview(@PathVariable Long id, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  WhyFunVisit visit = findVisit(id); if (reviews.findByVisitIdAndAuthorId(id, author.id).isPresent()) throw conflict("Ya existe una reseña de este autor para la visita");
  WhyFunVisitReview review = new WhyFunVisitReview(); review.visit = visit; review.author = review.updatedBy = author; review.createdAt = review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @PutMapping("/activity-visits/{id}/reviews/me") @Transactional ActivityReviewDto saveOwnReview(@PathVariable Long id, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  WhyFunVisit visit = findVisit(id); WhyFunVisitReview review = reviews.findByVisitIdAndAuthorId(id, author.id).orElseGet(() -> { WhyFunVisitReview value = new WhyFunVisitReview(); value.visit = visit; value.author = author; value.createdAt = Instant.now(); return value; });
  review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @PutMapping("/activity-visit-reviews/{reviewId}") @Transactional ActivityReviewDto updateReview(@PathVariable Long reviewId, @RequestBody @Valid ActivityReviewRequest request, @AuthenticationPrincipal User author) {
  WhyFunVisitReview review = reviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña")); review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @DeleteMapping("/activity-visit-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long reviewId) { reviews.delete(reviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña"))); }

 private WhyFunVenue findActivity(Long id) { return activities.findDetailedById(id).orElseThrow(() -> notFound("Actividad")); }
 private WhyFunVisit findVisit(Long id) { return visits.findDetailedById(id).orElseThrow(() -> notFound("Visita")); }
 private WhyFunCategory findCategory(Long id) { return categories.findDetailedById(id).orElseThrow(() -> notFound("Categoría")); }
  private void apply(WhyFunVenue activity, ActivityRequest request) {
  WhyFunCategory category = findCategory(request.categoryId()); WhyFunCategory subcategory = findCategory(request.subcategoryId());
  if (category.parent != null || !category.active) throw badRequest("Elegí una categoría principal activa");
  if (subcategory.parent == null || !subcategory.parent.id.equals(category.id) || !subcategory.active) throw badRequest("Elegí una subcategoría activa de la categoría seleccionada");
  activity.name = request.name().trim(); activity.address = request.address().trim(); activity.category = category; activity.subcategory = subcategory; activity.schedules.clear();
  if (request.schedules() != null) for (ActivityScheduleRequest source : request.schedules()) { if (source.opensAt().equals(source.closesAt())) throw badRequest("El horario de apertura y cierre debe ser distinto"); WhyFunVenueSchedule schedule = new WhyFunVenueSchedule(); schedule.venue = activity; schedule.dayOfWeek = source.dayOfWeek(); schedule.opensAt = source.opensAt(); schedule.closesAt = source.closesAt(); activity.schedules.add(schedule); }
  }
  private void touch(WhyFunVenue activity, User author) { activity.updatedBy = author; activity.updatedAt = Instant.now(); activities.save(activity); }
  private ActivityVisitDto visit(WhyFunVisit value) { return visit(value, activity(value.venue)); }
 private ActivityVisitDto visit(WhyFunVisit value, ActivityDto activity) {
  List<WhyFunVisitPhoto> visitPhotos = photos.findByVisitIdOrderByPositionAscIdAsc(value.id); List<ActivityPhotoDto> resultPhotos = visitPhotos.stream().map(WhyFunActivityApi::photo).toList();
  ActivityPhotoDto cover = resultPhotos.stream().filter(photo -> photo.id().equals(value.coverPhotoId)).findFirst().orElse(resultPhotos.isEmpty() ? null : resultPhotos.getFirst());
  List<WhyFunVisitReview> reviewValues = reviews.findByVisitIdOrderByAuthorUsername(value.id);
  Map<Long, String> reviewAuthors = reviews.authorsByVisitId(value.id).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
  return new ActivityVisitDto(value.id, activity, value.scheduledAt, value.createdBy.username, value.updatedBy.username, cover, resultPhotos, reviewValues.stream().map(review -> review(review, reviewAuthors.get(review.id))).toList(), value.createdAt, value.updatedAt);
 }
  private ActivityDto activity(WhyFunVenue value) { return activity(value, activityRatings(List.of(value.id)).get(value.id), activityVisitCounts(List.of(value.id)).getOrDefault(value.id, 0L)); }
  private ActivityDto activity(WhyFunVenue value, Double rating, long visitCount) { return new ActivityDto(value.id, value.name, value.address, category(value.category), category(value.subcategory), value.schedules.stream().sorted(Comparator.comparing((WhyFunVenueSchedule schedule) -> schedule.dayOfWeek).thenComparing(schedule -> schedule.opensAt)).map(schedule -> new ActivityScheduleDto(schedule.dayOfWeek, schedule.opensAt, schedule.closesAt)).toList(), profilePhoto(value).map(WhyFunActivityApi::profilePhoto).orElse(null), rating, visitCount, value.createdBy.username, value.updatedBy.username, value.createdAt, value.updatedAt); }
  private Map<Long, Double> activityRatings(Collection<Long> activityIds) { if (activityIds.isEmpty() || reviews == null) return Map.of(); return reviews.ratingsByActivityIdIn(activityIds).stream().collect(java.util.stream.Collectors.toMap(ActivityRating::getActivityId, ActivityRating::getRating)); }
  private Map<Long, Long> activityVisitCounts(Collection<Long> activityIds) { if (activityIds.isEmpty() || visits == null) return Map.of(); return visits.countsByActivityIdIn(activityIds).stream().collect(java.util.stream.Collectors.toMap(ActivityVisitCount::getActivityId, ActivityVisitCount::getVisitCount)); }
  private Optional<WhyFunVenuePhoto> profilePhoto(WhyFunVenue value) { return value.coverPhotoId == null ? Optional.empty() : activityPhotos.findByIdAndVenueId(value.coverPhotoId, value.id); }
  private static ActivityProfilePhotoDto profilePhoto(WhyFunVenuePhoto value) { return new ActivityProfilePhotoDto(value.id, "/why-fun/activities/" + value.venue.id + "/photo?v=" + value.id, "/why-fun/activities/" + value.venue.id + "/photo?thumbnail=true&v=" + value.id, value.width, value.height, value.createdAt); }
 private static FunCategoryDto category(WhyFunCategory value) { return new FunCategoryDto(value.id, value.parent == null ? null : value.parent.id, value.name, value.slug, value.icon, value.active); }
 private static ActivityPhotoDto photo(WhyFunVisitPhoto value) { return new ActivityPhotoDto(value.id, "/why-fun/activity-visit-photos/" + value.id, "/why-fun/activity-visit-photos/" + value.id + "?thumbnail=true", value.width, value.height, value.position, value.createdBy.username, value.createdAt); }
 private static ActivityReviewDto review(WhyFunVisitReview value) { return review(value, value.author.username); }
 private static ActivityReviewDto review(WhyFunVisitReview value, String author) { return new ActivityReviewDto(value.id, author, value.updatedBy.username, value.rating, value.comment, value.createdAt, value.updatedAt); }
 private static void apply(WhyFunVisitReview review, ActivityReviewRequest request) { review.rating = request.rating(); review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim(); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
