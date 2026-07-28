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

record WhenDateLabelDto(Long id, String label, SpecialDateRecurrence recurrence) {}
record WhenDateSourcePhotoDto(String id, String url, String thumbnailUrl, int width, int height) {}
record WhenDateEntryDto(String id, String section, Long entityId, Long experienceId, LocalDate date, String title, String detail, String imageUrl, String href, List<WhenDateLabelDto> specialDates, List<WhenDateSourcePhotoDto> sourcePhotos, Map<Long, String> occurrenceCoverUrls) {}
record WhenDateOccurrenceSummaryDto(WhenDateLabelDto specialDate, LocalDate occurredOn, int experienceCount, String imageUrl) {}
record SpecialDateOccurrencePhotoDto(Long id, String url, String thumbnailUrl, int width, int height, int position, String createdBy, Instant createdAt) {}
record SpecialDateOccurrenceCommentDto(Long id, String author, String updatedBy, String comment, Instant createdAt, Instant updatedAt) {}
record WhenDateOccurrenceDto(Long id, WhenDateLabelDto specialDate, LocalDate occurredOn, List<WhenDateEntryDto> entries, List<SpecialDateOccurrencePhotoDto> photos, SpecialDateOccurrencePhotoDto coverPhoto, List<SpecialDateOccurrenceCommentDto> comments, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {}
record WhenDateCommentRequest(@NotBlank @Size(max = 2000) String comment) {}

@RestController
@RequestMapping("/api/when-dates")
public class WhenDatesApi {
 private static final int MAX_PHOTOS = 4;
 private final SpecialDates specialDates;
 private final SpecialDateOccurrences occurrences;
 private final SpecialDateOccurrenceComments comments;
 private final SpecialDateOccurrencePhotos photos;
 private final PlaceVisits placeVisits;
 private final FilmViews filmViews;
 private final Cookings cookings;
  private final WhyFunVisits funVisits;
  private final PlacePhotos placePhotos;
  private final PlaceVisitPhotos visitPhotos;
  private final FilmPhotos filmPhotos;
  private final RecipePhotos recipePhotos;
  private final WhyFunVenuePhotos funPhotos;
  private final WhyFunVisitPhotos funVisitPhotos;
  private final PhotoStorage storage;

  public WhenDatesApi(SpecialDates specialDates, SpecialDateOccurrences occurrences, SpecialDateOccurrenceComments comments, SpecialDateOccurrencePhotos photos, PlaceVisits placeVisits, FilmViews filmViews, Cookings cookings, WhyFunVisits funVisits, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, FilmPhotos filmPhotos, RecipePhotos recipePhotos, WhyFunVenuePhotos funPhotos, WhyFunVisitPhotos funVisitPhotos, PhotoStorage storage) {
   this.specialDates = specialDates; this.occurrences = occurrences; this.comments = comments; this.photos = photos; this.placeVisits = placeVisits; this.filmViews = filmViews; this.cookings = cookings; this.funVisits = funVisits; this.placePhotos = placePhotos; this.visitPhotos = visitPhotos; this.filmPhotos = filmPhotos; this.recipePhotos = recipePhotos; this.funPhotos = funPhotos; this.funVisitPhotos = funVisitPhotos; this.storage = storage;
 }

  @GetMapping @Transactional(readOnly = true) Slice<WhenDateOccurrenceSummaryDto> list(@RequestParam(required = false) Long specialDateId, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
   List<WhenDateOccurrenceSummaryDto> summaries = summaries(entries(null, null, specialDateId), specialDateId);
   int limit = Math.max(1, Math.min(size, 30)); int offset = cursor == null ? 0 : Math.max(0, cursor.intValue());
   List<WhenDateOccurrenceSummaryDto> page = summaries.stream().skip(offset).limit(limit + 1L).toList();
   return new Slice<>(page.stream().limit(limit).toList(), page.size() > limit ? (long) offset + limit : null);
  }

 @GetMapping("/special-dates/{specialDateId}/occurrences/{occurredOn}") @Transactional(readOnly = true) WhenDateOccurrenceDto occurrence(@PathVariable Long specialDateId, @PathVariable LocalDate occurredOn) {
  SpecialDate specialDate = specialDate(specialDateId); validateOccurrence(specialDate, occurredOn);
  return occurrenceDto(specialDate, occurredOn, occurrences.findBySpecialDateIdAndOccurredOn(specialDateId, occurredOn).orElse(null));
 }

 @PutMapping("/special-dates/{specialDateId}/occurrences/{occurredOn}/comments/me") @Transactional WhenDateOccurrenceDto saveComment(@PathVariable Long specialDateId, @PathVariable LocalDate occurredOn, @RequestBody @Valid WhenDateCommentRequest request, @AuthenticationPrincipal User author) {
  SpecialDate specialDate = specialDate(specialDateId); SpecialDateOccurrence occurrence = ensureOccurrence(specialDate, occurredOn, author);
  SpecialDateOccurrenceComment comment = comments.findByOccurrenceIdAndAuthorId(occurrence.id, author.id).orElseGet(() -> { SpecialDateOccurrenceComment value = new SpecialDateOccurrenceComment(); value.occurrence = occurrence; value.author = author; value.createdAt = Instant.now(); return value; });
  comment.comment = request.comment().trim(); comment.updatedBy = author; comment.updatedAt = Instant.now(); comments.save(comment); touch(occurrence, author);
  return occurrenceDto(specialDate, occurredOn, occurrence);
 }

 @DeleteMapping("/special-dates/{specialDateId}/occurrences/{occurredOn}/comments/me") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteComment(@PathVariable Long specialDateId, @PathVariable LocalDate occurredOn, @AuthenticationPrincipal User author) {
  SpecialDateOccurrence occurrence = occurrences.findBySpecialDateIdAndOccurredOn(specialDateId, occurredOn).orElseThrow(() -> notFound("Recuerdo")); comments.findByOccurrenceIdAndAuthorId(occurrence.id, author.id).ifPresent(comments::delete); touch(occurrence, author);
 }

 @PostMapping(value = "/special-dates/{specialDateId}/occurrences/{occurredOn}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional WhenDateOccurrenceDto uploadPhoto(@PathVariable Long specialDateId, @PathVariable LocalDate occurredOn, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
  SpecialDate specialDate = specialDate(specialDateId); SpecialDateOccurrence occurrence = ensureOccurrence(specialDate, occurredOn, author);
  List<SpecialDateOccurrencePhoto> current = photos.findByOccurrenceIdOrderByPositionAscIdAsc(occurrence.id); if (current.size() >= MAX_PHOTOS) throw conflict("Esta fecha admite hasta " + MAX_PHOTOS + " fotos");
  int position = current.isEmpty() ? 0 : current.getLast().position + 1; SpecialDateOccurrencePhoto photo = photos.saveAndFlush(storage.store(occurrence, author, position, file)); if (occurrence.coverPhotoId == null) occurrence.coverPhotoId = photo.id; touch(occurrence, author);
  return occurrenceDto(specialDate, occurredOn, occurrence);
 }

 @PutMapping("/occurrences/{occurrenceId}/cover/{photoId}") @Transactional WhenDateOccurrenceDto setCover(@PathVariable Long occurrenceId, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
  SpecialDateOccurrence occurrence = findOccurrence(occurrenceId); SpecialDateOccurrencePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); if (!photo.occurrence.id.equals(occurrence.id)) throw badRequest("La foto no pertenece a esta fecha"); occurrence.coverPhotoId = photo.id; touch(occurrence, author); return occurrenceDto(occurrence.specialDate, occurrence.occurredOn, occurrence);
 }

 @DeleteMapping("/photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
  SpecialDateOccurrencePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); SpecialDateOccurrence occurrence = photo.occurrence; boolean wasCover = photo.id.equals(occurrence.coverPhotoId); photos.delete(photo); photos.flush(); if (wasCover) occurrence.coverPhotoId = photos.findByOccurrenceIdOrderByPositionAscIdAsc(occurrence.id).stream().findFirst().map(value -> value.id).orElse(null); touch(occurrence, author);
 }

 @GetMapping(value = "/photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
  SpecialDateOccurrencePhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

  private List<WhenDateEntryDto> entries(LocalDate from, LocalDate to, Long requestedSpecialDateId) {
  List<SpecialDate> dates = specialDates.findAllByOrderByDateAscLabelAscIdAsc(); if (requestedSpecialDateId != null) dates = dates.stream().filter(value -> value.id.equals(requestedSpecialDateId)).toList();
  LocalDate today = RosarioClock.today(); List<WhenDateEntryDto> result = new ArrayList<>();
   for (PlaceVisit visit : placeVisits.findAll()) add(result, "FOOD", visit.id, visit.place.id, visit.visitedOn, visit.place.name, visit.place.address, placeImage(visit), "/food/places/" + visit.place.id, dates, from, to, today, placeSourcePhotos(visit));
   for (FilmView view : filmViews.findAll()) add(result, "FILM", view.id, view.film.id, view.watchedOn, view.film.title, view.film.platform == null ? "Película vista" : view.film.platform.icon + " " + view.film.platform.name, filmImage(view.film), "/films/" + view.film.id, dates, from, to, today, filmSourcePhotos(view.film));
   for (Cooking cooking : cookings.findAll()) add(result, "COOK", cooking.id, cooking.recipe.id, cooking.cookedOn, cooking.recipe.name, cooking.home == Home.TOMAS ? "Casa de Tomás" : "Casa de Avril", recipeImage(cooking.recipe), "/how-cook/" + cooking.recipe.id, dates, from, to, today, recipeSourcePhotos(cooking.recipe));
   for (WhyFunVisit visit : funVisits.findAll()) add(result, "FUN", visit.id, visit.venue.id, visit.scheduledAt, visit.venue.name, visit.venue.address, funImage(visit), "/why-fun/" + visit.venue.id, dates, from, to, today, funSourcePhotos(visit));
    Map<String, String> coverUrls = from == null || to == null ? Map.of() : occurrenceCoverUrls(result, from, to);
    return result.stream().map(entry -> entry(entry, coverUrls)).sorted(Comparator.comparing(WhenDateEntryDto::date).reversed().thenComparing(WhenDateEntryDto::section).thenComparing(WhenDateEntryDto::experienceId)).toList();
  }

  private List<WhenDateOccurrenceSummaryDto> summaries(List<WhenDateEntryDto> entries, Long requestedSpecialDateId) {
   Map<String, List<WhenDateEntryDto>> grouped = new LinkedHashMap<>();
   for (WhenDateEntryDto entry : entries) for (WhenDateLabelDto specialDate : entry.specialDates()) grouped.computeIfAbsent(coverKey(specialDate.id(), entry.date()), ignored -> new ArrayList<>()).add(entry);
   Map<String, WhenDateOccurrenceSummaryDto> result = new HashMap<>();
   for (Map.Entry<String, List<WhenDateEntryDto>> group : grouped.entrySet()) {
    List<WhenDateEntryDto> groupedEntries = group.getValue(); WhenDateEntryDto first = groupedEntries.getFirst(); WhenDateLabelDto specialDate = first.specialDates().stream().filter(value -> group.getKey().equals(coverKey(value.id(), first.date()))).findFirst().orElseThrow();
    String imageUrl = groupedEntries.stream().map(WhenDateEntryDto::imageUrl).filter(Objects::nonNull).findFirst().orElse(null);
    result.put(coverKey(specialDate.id(), first.date()), new WhenDateOccurrenceSummaryDto(specialDate, first.date(), groupedEntries.size(), imageUrl));
   }
   for (SpecialDateOccurrence occurrence : occurrences.findAllByOrderByOccurredOnDescIdDesc()) {
    if (requestedSpecialDateId != null && !occurrence.specialDate.id.equals(requestedSpecialDateId)) continue;
    String key = coverKey(occurrence.specialDate.id, occurrence.occurredOn); WhenDateOccurrenceSummaryDto current = result.get(key);
    result.put(key, new WhenDateOccurrenceSummaryDto(label(occurrence.specialDate), occurrence.occurredOn, current == null ? 0 : current.experienceCount(), occurrence.coverPhotoId == null ? current == null ? null : current.imageUrl() : "/when-dates/photos/" + occurrence.coverPhotoId));
   }
   return result.values().stream().sorted(Comparator.comparing(WhenDateOccurrenceSummaryDto::occurredOn).reversed().thenComparing(value -> value.specialDate().label()).thenComparing(value -> value.specialDate().id())).toList();
  }

  private void add(List<WhenDateEntryDto> result, String section, Long experienceId, Long entityId, LocalDate date, String title, String detail, String imageUrl, String href, List<SpecialDate> dates, LocalDate from, LocalDate to, LocalDate today, List<WhenDateSourcePhotoDto> sourcePhotos) {
     if (date == null || date.isAfter(today) || (from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) return; List<WhenDateLabelDto> matches = dates.stream().filter(value -> matches(value, date)).map(WhenDatesApi::label).toList(); if (!matches.isEmpty()) result.add(new WhenDateEntryDto(section + ":" + experienceId, section, entityId, experienceId, date, title, detail, imageUrl, href, matches, sourcePhotos, Map.of()));
 }

  private WhenDateOccurrenceDto occurrenceDto(SpecialDate specialDate, LocalDate occurredOn, SpecialDateOccurrence occurrence) {
  if (occurrence == null) return new WhenDateOccurrenceDto(null, label(specialDate), occurredOn, entries(occurredOn, occurredOn, specialDate.id), List.of(), null, List.of(), null, null, null, null);
  List<SpecialDateOccurrencePhotoDto> occurrencePhotos = photos.findByOccurrenceIdOrderByPositionAscIdAsc(occurrence.id).stream().map(WhenDatesApi::photo).toList(); SpecialDateOccurrencePhotoDto cover = occurrencePhotos.stream().filter(value -> value.id().equals(occurrence.coverPhotoId)).findFirst().orElse(null);
  List<SpecialDateOccurrenceCommentDto> occurrenceComments = comments.findByOccurrenceIdOrderByAuthorUsername(occurrence.id).stream().map(WhenDatesApi::comment).toList();
  return new WhenDateOccurrenceDto(occurrence.id, label(specialDate), occurredOn, entries(occurredOn, occurredOn, specialDate.id), occurrencePhotos, cover, occurrenceComments, occurrence.createdBy.username, occurrence.updatedBy.username, occurrence.createdAt, occurrence.updatedAt);
  }

  private Map<String, String> occurrenceCoverUrls(List<WhenDateEntryDto> entries, LocalDate from, LocalDate to) {
   List<Long> specialDateIds = entries.stream().flatMap(entry -> entry.specialDates().stream()).map(WhenDateLabelDto::id).distinct().toList();
   if (specialDateIds.isEmpty()) return Map.of();
   return occurrences.findBySpecialDateIdInAndOccurredOnBetween(specialDateIds, from, to).stream().filter(occurrence -> occurrence.coverPhotoId != null).collect(java.util.stream.Collectors.toMap(occurrence -> coverKey(occurrence.specialDate.id, occurrence.occurredOn), occurrence -> "/when-dates/photos/" + occurrence.coverPhotoId));
  }
  private static WhenDateEntryDto entry(WhenDateEntryDto value, Map<String, String> coverUrls) { Map<Long, String> entryCovers = new HashMap<>(); for (WhenDateLabelDto label : value.specialDates()) { String coverUrl = coverUrls.get(coverKey(label.id(), value.date())); if (coverUrl != null) entryCovers.put(label.id(), coverUrl); } return new WhenDateEntryDto(value.id(), value.section(), value.entityId(), value.experienceId(), value.date(), value.title(), value.detail(), value.imageUrl(), value.href(), value.specialDates(), value.sourcePhotos(), entryCovers); }
  private static String coverKey(Long specialDateId, LocalDate occurredOn) { return specialDateId + ":" + occurredOn; }

 private SpecialDateOccurrence ensureOccurrence(SpecialDate specialDate, LocalDate occurredOn, User author) {
  validateOccurrence(specialDate, occurredOn); return occurrences.findBySpecialDateIdAndOccurredOn(specialDate.id, occurredOn).orElseGet(() -> { SpecialDateOccurrence value = new SpecialDateOccurrence(); value.specialDate = specialDate; value.occurredOn = occurredOn; value.createdBy = value.updatedBy = author; value.createdAt = value.updatedAt = Instant.now(); return occurrences.save(value); });
 }
 private SpecialDateOccurrence findOccurrence(Long id) { return occurrences.findById(id).orElseThrow(() -> notFound("Recuerdo")); }
 private SpecialDate specialDate(Long id) { return specialDates.findById(id).orElseThrow(() -> notFound("Fecha especial")); }
 private void touch(SpecialDateOccurrence occurrence, User author) { occurrence.updatedBy = author; occurrence.updatedAt = Instant.now(); occurrences.save(occurrence); }
 private void validateOccurrence(SpecialDate specialDate, LocalDate occurredOn) { if (occurredOn.isAfter(RosarioClock.today())) throw badRequest("La fecha todavía no ocurrió"); if (!matches(specialDate, occurredOn)) throw badRequest("La fecha no coincide con esta fecha especial"); }
 private static boolean matches(SpecialDate specialDate, LocalDate date) { return switch (specialDate.recurrence) { case ONCE -> specialDate.date.equals(date); case ANNUAL -> specialDate.date.getMonthValue() == date.getMonthValue() && specialDate.date.getDayOfMonth() == date.getDayOfMonth(); case MONTHLY -> specialDate.date.getDayOfMonth() == date.getDayOfMonth(); }; }
  private String placeImage(PlaceVisit visit) { if (visit.coverPhotoId != null) return "/place-visit-photos/" + visit.coverPhotoId; return placePhotos.findByPlaceId(visit.place.id).isPresent() ? "/places/" + visit.place.id + "/photo" : null; }
  private String filmImage(Film film) { return filmPhotos.findByFilmId(film.id).isPresent() ? "/films/" + film.id + "/photo" : film.posterPath; }
  private String recipeImage(Recipe recipe) { return recipePhotos.findByRecipeId(recipe.id).isPresent() ? "/how-cook/recipes/" + recipe.id + "/photo" : null; }
  private String funImage(WhyFunVisit visit) { if (visit.coverPhotoId != null) return "/why-fun/activity-visit-photos/" + visit.coverPhotoId; return funPhotos.findByVenueIdOrderByIdAsc(visit.venue.id).isEmpty() ? null : "/why-fun/activities/" + visit.venue.id + "/photo"; }
  private List<WhenDateSourcePhotoDto> placeSourcePhotos(PlaceVisit visit) { List<WhenDateSourcePhotoDto> result = visitPhotos.findByVisitIdOrderByPositionAscIdAsc(visit.id).stream().map(photo -> source("FOOD:VISIT:" + photo.id, "/place-visit-photos/" + photo.id, "/place-visit-photos/" + photo.id + "?thumbnail=true", photo.width, photo.height)).toList(); return result.isEmpty() ? placePhotos.findByPlaceId(visit.place.id).map(photo -> List.of(source("FOOD:PLACE:" + photo.id, "/places/" + visit.place.id + "/photo?v=" + photo.id, "/places/" + visit.place.id + "/photo?thumbnail=true&v=" + photo.id, photo.width, photo.height))).orElse(List.of()) : result; }
  private List<WhenDateSourcePhotoDto> filmSourcePhotos(Film film) { return filmPhotos.findByFilmId(film.id).map(photo -> List.of(source("FILM:" + photo.id, "/films/" + film.id + "/photo", "/films/" + film.id + "/photo?thumbnail=true", photo.width, photo.height))).orElse(film.posterPath == null ? List.of() : List.of(source("FILM:POSTER:" + film.id, film.posterPath, film.posterPath, 0, 0))); }
  private List<WhenDateSourcePhotoDto> recipeSourcePhotos(Recipe recipe) { return recipePhotos.findByRecipeId(recipe.id).map(photo -> List.of(source("COOK:" + photo.id, "/how-cook/recipes/" + recipe.id + "/photo", "/how-cook/recipes/" + recipe.id + "/photo?thumbnail=true", photo.width, photo.height))).orElse(List.of()); }
  private List<WhenDateSourcePhotoDto> funSourcePhotos(WhyFunVisit visit) { List<WhenDateSourcePhotoDto> result = funVisitPhotos.findByVisitIdOrderByPositionAscIdAsc(visit.id).stream().map(photo -> source("FUN:VISIT:" + photo.id, "/why-fun/activity-visit-photos/" + photo.id, "/why-fun/activity-visit-photos/" + photo.id + "?thumbnail=true", photo.width, photo.height)).toList(); return result.isEmpty() ? funPhotos.findByVenueIdOrderByIdAsc(visit.venue.id).stream().map(photo -> source("FUN:VENUE:" + photo.id, "/why-fun/photos/" + photo.id, "/why-fun/photos/" + photo.id + "?thumbnail=true", photo.width, photo.height)).toList() : result; }
  private static WhenDateSourcePhotoDto source(String id, String url, String thumbnailUrl, int width, int height) { return new WhenDateSourcePhotoDto(id, url, thumbnailUrl, width, height); }
 private static WhenDateLabelDto label(SpecialDate value) { return new WhenDateLabelDto(value.id, value.label, value.recurrence); }
 private static SpecialDateOccurrencePhotoDto photo(SpecialDateOccurrencePhoto value) { return new SpecialDateOccurrencePhotoDto(value.id, "/when-dates/photos/" + value.id, "/when-dates/photos/" + value.id + "?thumbnail=true", value.width, value.height, value.position, value.createdBy.username, value.createdAt); }
 private static SpecialDateOccurrenceCommentDto comment(SpecialDateOccurrenceComment value) { return new SpecialDateOccurrenceCommentDto(value.id, value.author.username, value.updatedBy.username, value.comment, value.createdAt, value.updatedAt); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
