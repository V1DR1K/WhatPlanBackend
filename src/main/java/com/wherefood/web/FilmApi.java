package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record PlatformRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String icon, boolean active) {}
record PlatformDto(Long id, String name, String icon, boolean active) {}
record FilmRequest(Long tmdbId, @Size(max = 200) String title, @Size(max = 200) String originalTitle, @Size(max = 3000) String synopsis, LocalDate releaseDate, @Size(max = 300) String posterPath, LocalDate watchedOn, List<@Size(max = 80) String> genres, Long platformId) {}
record FilmViewRequest(@NotNull LocalDate watchedOn) {}
record FilmReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment, LocalDate watchedOn, @Size(max = 300) String favoriteCharacter, Map<@NotBlank @Pattern(regexp = "[a-z_]{1,80}") String, @NotNull @Min(1) @Max(5) Short> metrics) {}
record FilmGenreOptionRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String emoji) {}
record FilmGenreOptionDto(Long id, String name, String emoji) {}
record FilmReviewDto(Long id, String author, short rating, String comment, LocalDate watchedOn, String favoriteCharacter, Map<String, Short> metrics) {}
record FilmViewDto(Long id, LocalDate watchedOn, String createdBy, String updatedBy, List<FilmReviewDto> reviews, Instant createdAt) {}
 record FilmDto(Long id, Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterUrl, String thumbnailUrl, Integer posterWidth, Integer posterHeight, List<String> genres, PlatformDto platform, int watchedCount, LocalDate lastWatchedOn, String author, List<FilmReviewDto> reviews, List<FilmViewDto> views, Instant createdAt, Instant updatedAt, TmdbMovieDto tmdb) {}

@RestController
@RequestMapping("/api")
public class FilmApi {
 private final Films films;
 private final FilmReviews reviews;
 private final FilmViews views;
 private final WatchPlatforms platforms;
   private final FilmPhotos filmPhotos;
  private final FilmGenreOptions genreOptions;
  private final PhotoStorage storage;
  private final TmdbClient tmdb;

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb) {
    this.films = films; this.reviews = reviews; this.views = views; this.platforms = platforms; this.filmPhotos = filmPhotos; this.genreOptions = genreOptions; this.storage = storage; this.tmdb = tmdb;
   }

  @GetMapping("/tmdb/movies") List<TmdbMovieDto> searchTmdb(@RequestParam String query) { return tmdb.search(query); }
  @GetMapping("/watch-platforms") List<PlatformDto> activePlatforms() { return platforms.findByActiveTrueOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @GetMapping("/watch-platforms/all") @PreAuthorize("hasRole('ADMIN')") List<PlatformDto> allPlatforms() { return platforms.findAllByOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @PostMapping("/watch-platforms") @PreAuthorize("hasRole('ADMIN')") PlatformDto addPlatform(@RequestBody @Valid PlatformRequest request) { WatchPlatform value = new WatchPlatform(); apply(value, request); value.createdAt = Instant.now(); return platform(platforms.save(value)); }
 @PutMapping("/watch-platforms/{id}") @PreAuthorize("hasRole('ADMIN')") PlatformDto updatePlatform(@PathVariable Long id, @RequestBody @Valid PlatformRequest request) { WatchPlatform value = platforms.findById(id).orElseThrow(() -> notFound("Plataforma")); apply(value, request); return platform(platforms.save(value)); }
  @GetMapping("/film-genres") List<FilmGenreOptionDto> genres() { return genreOptions.findAllByOrderByNameAsc().stream().map(FilmApi::genre).toList(); }
  @PostMapping("/film-genres") @PreAuthorize("hasRole('ADMIN')") FilmGenreOptionDto addGenre(@RequestBody @Valid FilmGenreOptionRequest request) { FilmGenreOption value = new FilmGenreOption(); apply(value, request); value.createdAt = Instant.now(); return genre(genreOptions.save(value)); }
  @PutMapping("/film-genres/{id}") @PreAuthorize("hasRole('ADMIN')") FilmGenreOptionDto updateGenre(@PathVariable Long id, @RequestBody @Valid FilmGenreOptionRequest request) { FilmGenreOption value = genreOptions.findById(id).orElseThrow(() -> notFound("Género")); apply(value, request); return genre(genreOptions.save(value)); }
  @DeleteMapping("/film-genres/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteGenre(@PathVariable Long id) { genreOptions.delete(genreOptions.findById(id).orElseThrow(() -> notFound("Género"))); }

 @GetMapping("/films") List<FilmDto> list(@RequestParam(required = false) String genre, @RequestParam(required = false) Long platformId, @RequestParam(required = false) Boolean watched) {
  return films.findAll().stream()
    .filter(film -> platformId == null || (film.platform != null && film.platform.id.equals(platformId)))
    .filter(film -> watched == null || watched == (film.watchedCount > 0))
    .filter(film -> genre == null || genre.isBlank() || matchesGenre(film, genre))
     .sorted(Comparator.comparing((Film film) -> film.lastWatchedOn, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(film -> film.updatedAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(film -> film.createdAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(film -> film.id, Comparator.reverseOrder()))
    .map(film -> film(film, false)).toList();
  }

  @GetMapping("/films/{id}") FilmDto get(@PathVariable Long id) { return film(findFilm(id), true); }
  @GetMapping(value = "/films/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   FilmPhoto photo = filmPhotos.findByFilmId(id).orElseThrow(() -> notFound("Foto"));
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping("/films") @ResponseStatus(HttpStatus.CREATED) FilmDto add(@RequestBody @Valid FilmRequest request, @AuthenticationPrincipal User author) {
   assertAvailableTmdbId(request.tmdbId(), null);
   Film film = new Film();
    apply(film, request); film.createdBy = film.updatedBy = author; film.createdAt = film.updatedAt = Instant.now();
   return film(films.save(film), true);
  }
  @PutMapping("/films/{id}") FilmDto update(@PathVariable Long id, @RequestBody @Valid FilmRequest request, @AuthenticationPrincipal User author) {
    Film film = findFilm(id); assertAvailableTmdbId(request.tmdbId(), id); apply(film, request); film.updatedBy = author; film.updatedAt = Instant.now(); return film(films.save(film), true);
  }
  @DeleteMapping("/films/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id, @AuthenticationPrincipal User author) { films.delete(findFilm(id)); }
 @PostMapping(value = "/films/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional FilmDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws java.io.IOException {
   Film film = findFilm(id); filmPhotos.findByFilmId(id).ifPresent(filmPhotos::delete); filmPhotos.flush(); film.updatedBy = user; film.updatedAt = Instant.now(); films.save(film); filmPhotos.save(storage.store(film, file)); return film(film);
 }

   @PostMapping("/films/{id}/views") @Transactional FilmViewDto addView(@PathVariable Long id, @RequestBody @Valid FilmViewRequest request, @AuthenticationPrincipal User author) {
     FilmView view = createView(findFilm(id), request, author); return view(view, List.of());
  }
  @PutMapping("/films/{filmId}/views/{viewId}") @Transactional FilmViewDto updateView(@PathVariable Long filmId, @PathVariable Long viewId, @RequestBody @Valid FilmViewRequest request, @AuthenticationPrincipal User author) {
    FilmView view = findView(filmId, viewId);
   validateViewMoment(request);
    views.findByFilmIdAndWatchedOn(filmId, request.watchedOn()).filter(other -> !other.id.equals(view.id)).ifPresent(other -> { throw conflict("Ya registraron una vista para esa fecha"); });
     view.watchedOn = request.watchedOn(); view.updatedBy = author;
     view.film.updatedBy = author; FilmView saved = views.save(view); refreshWatchSummary(saved.film); return view(saved, reviews.findByFilmIdOrderByViewWatchedOnDescIdDesc(filmId).stream().filter(review -> review.view.id.equals(saved.id)).map(FilmApi::review).toList());
  }
  @DeleteMapping("/films/{filmId}/views/{viewId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deleteView(@PathVariable Long filmId, @PathVariable Long viewId, @AuthenticationPrincipal User author) {
    FilmView view = findView(filmId, viewId); Film film = view.film; film.updatedBy = author; views.delete(view); views.flush(); refreshWatchSummary(film);
  }

 @PostMapping("/films/{filmId}/views/{viewId}/reviews") @Transactional FilmReviewDto addReview(@PathVariable Long filmId, @PathVariable Long viewId, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
  Film film = findFilm(filmId);
  return saveReview(film, findView(filmId, viewId), request, author);
 }

 @PostMapping("/films/{id}/reviews") @Transactional FilmReviewDto saveLegacyReview(@PathVariable Long id, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
  Film film = findFilm(id);
   LocalDate watchedOn = request.watchedOn() == null ? RosarioClock.today() : request.watchedOn();
   FilmView view = views.findByFilmIdAndWatchedOn(id, watchedOn).orElseGet(() -> createView(film, new FilmViewRequest(watchedOn), author));
  return saveReview(film, view, request, author);
 }

  @PutMapping("/films/{filmId}/reviews/{reviewId}") @Transactional FilmReviewDto updateReview(@PathVariable Long filmId, @PathVariable Long reviewId, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
    FilmReview review = reviews.findByIdAndFilmId(reviewId, filmId).orElseThrow(() -> notFound("Reseña"));
   review.rating = request.rating(); review.comment = emptyToNull(request.comment()); review.favoriteCharacter = favoriteCharacter(review.film, request.favoriteCharacter()); review.metrics.clear(); if (request.metrics() != null) review.metrics.putAll(request.metrics()); review.updatedBy = author; review.updatedAt = Instant.now();
   return review(reviews.save(review));
  }
  @DeleteMapping("/films/{filmId}/reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long filmId, @PathVariable Long reviewId) { reviews.delete(reviews.findByIdAndFilmId(reviewId, filmId).orElseThrow(() -> notFound("Reseña"))); }

  private Film findFilm(Long id) { return films.findDetailedById(id).orElseThrow(() -> notFound("Película")); }
  private void assertAvailableTmdbId(Long tmdbId, Long currentId) {
   if (tmdbId == null) return;
   films.findByTmdbId(tmdbId).filter(existing -> !existing.id.equals(currentId)).ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa película ya está en WhichFilm"); });
  }
  private FilmDto film(Film film) { return film(film, true); }
  private FilmDto film(Film film, boolean detailedTmdb) {
   List<FilmReview> reviewValues = reviews.findByFilmIdOrderByViewWatchedOnDescIdDesc(film.id);
   Map<Long, String> reviewAuthors = reviews.authorsByFilmId(film.id).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   List<FilmReviewDto> filmReviews = reviewValues.stream().map(review -> review(review, reviewAuthors.get(review.id))).toList();
   Map<Long, List<FilmReviewDto>> reviewsByView = reviewValues.stream().collect(java.util.stream.Collectors.groupingBy(review -> review.view.id, java.util.stream.Collectors.mapping(review -> review(review, reviewAuthors.get(review.id)), java.util.stream.Collectors.toList())));
    List<FilmViewDto> filmViews = views.findByFilmIdOrderByWatchedOnDescIdDesc(film.id).stream().map(view -> view(view, reviewsByView.getOrDefault(view.id, List.of()))).toList();
    FilmPhoto photo = filmPhotos.findByFilmId(film.id).orElse(null);
   TmdbMovieDto catalog = catalog(film.tmdbId, detailedTmdb);
    String posterUrl = photo != null ? photoUrl(film.id, false, photo.id) : posterUrl(film.posterPath);
    String thumbnailUrl = photo != null ? photoUrl(film.id, true, photo.id) : null;
    Integer posterWidth = photo == null ? null : Integer.valueOf(photo.width);
    Integer posterHeight = photo == null ? null : Integer.valueOf(photo.height);
      return new FilmDto(film.id, film.tmdbId, film.title, film.originalTitle, film.synopsis, film.releaseDate, posterUrl, thumbnailUrl, posterWidth, posterHeight, film.genres.stream().map(value -> value.name).sorted(String.CASE_INSENSITIVE_ORDER).toList(), film.platform == null ? null : platform(film.platform), film.watchedCount, film.lastWatchedOn, film.createdBy.username, filmReviews, filmViews, film.createdAt, film.updatedAt, catalog);
  }
  private void apply(Film film, FilmRequest request) {
   if (request.tmdbId() == null) {
    if (request.title() == null || request.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá el título de la película");
    film.tmdbId = null; film.title = request.title().trim(); film.originalTitle = blankToNull(request.originalTitle()); film.synopsis = blankToNull(request.synopsis()); film.releaseDate = request.releaseDate(); film.posterPath = blankToNull(request.posterPath());
    Set<String> names = request.genres() == null ? Set.of() : request.genres().stream().map(String::trim).filter(value -> !value.isBlank()).limit(12).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<FilmGenreOption> selected = names.isEmpty() ? List.of() : genreOptions.findAllByNameIn(names);
    if (selected.size() != names.size()) throw notFound("Género");
    film.genres.clear(); film.genres.addAll(selected);
   } else {
    TmdbMovieDto source = tmdb.details(request.tmdbId());
    if (source.title() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TMDB no devolvió un título para esa película");
    film.tmdbId = source.tmdbId(); film.title = source.title(); film.originalTitle = null; film.synopsis = null; film.releaseDate = null; film.posterPath = null; film.genres.clear();
   }
  film.platform = request.platformId() == null ? null : platforms.findById(request.platformId()).orElseThrow(() -> notFound("Plataforma"));
  }
  private TmdbMovieDto catalog(Long tmdbId, boolean detailed) {
   if (tmdbId == null) return null;
   try { return detailed ? tmdb.details(tmdbId) : tmdb.summary(tmdbId); }
   catch (ResponseStatusException ignored) { return null; }
  }
  private boolean matchesGenre(Film film, String genre) {
   if (film.genres.stream().anyMatch(value -> value.name.equalsIgnoreCase(genre))) return true;
   TmdbMovieDto catalog = catalog(film.tmdbId, false);
   return catalog != null && catalog.genres().stream().anyMatch(value -> value.equalsIgnoreCase(genre));
  }
  private static String posterUrl(String posterPath) { return posterPath; }
    private static String photoUrl(Long filmId, boolean thumbnail, Long photoId) { return "/films/" + filmId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
  private static PlatformDto platform(WatchPlatform value) { return new PlatformDto(value.id, value.name, value.icon, value.active); }
  private static FilmGenreOptionDto genre(FilmGenreOption value) { return new FilmGenreOptionDto(value.id, value.name, value.emoji); }
  private static void apply(FilmGenreOption value, FilmGenreOptionRequest request) { value.name = request.name().trim(); value.emoji = request.emoji().trim(); }
   private FilmView createView(Film film, FilmViewRequest request, User author) {
    validateViewMoment(request);
    if (views.findByFilmIdAndWatchedOn(film.id, request.watchedOn()).isPresent()) throw conflict("Ya registraron una vista para esa fecha");
     FilmView view = new FilmView(); view.film = film; film.updatedBy = author; view.createdBy = view.updatedBy = author; view.watchedOn = request.watchedOn(); view.createdAt = Instant.now();
    FilmView saved = views.save(view); refreshWatchSummary(film);
    return saved;
   }
  private FilmReviewDto saveReview(Film film, FilmView view, FilmReviewRequest request, User author) {
   if (reviews.existsByViewIdAndAuthorId(view.id, author.id)) throw conflict("Ya dejaste tu reseña para esta vista");
    FilmReview review = new FilmReview(); review.film = film; review.view = view; review.author = review.updatedBy = author; review.createdAt = Instant.now();
    review.rating = request.rating(); review.comment = emptyToNull(request.comment()); review.favoriteCharacter = favoriteCharacter(film, request.favoriteCharacter()); if (request.metrics() != null) review.metrics.putAll(request.metrics()); review.updatedAt = Instant.now(); film.updatedBy = author; film.updatedAt = Instant.now(); films.save(film);
   return review(reviews.save(review));
  }
  private FilmView findView(Long filmId, Long viewId) { return views.findByIdAndFilmId(viewId, filmId).orElseThrow(() -> notFound("Vista")); }
   private static FilmViewDto view(FilmView value, List<FilmReviewDto> reviews) { return new FilmViewDto(value.id, value.watchedOn, value.createdBy.username, value.updatedBy == null ? value.createdBy.username : value.updatedBy.username, reviews, value.createdAt); }
   private static FilmReviewDto review(FilmReview value) { return review(value, value.author.username); }
   private static FilmReviewDto review(FilmReview value, String author) { return new FilmReviewDto(value.id, author, value.rating, value.comment, value.view.watchedOn, value.favoriteCharacter, Map.copyOf(value.metrics)); }
 private static void apply(WatchPlatform value, PlatformRequest request) { value.name = request.name().trim(); value.icon = request.icon().trim(); value.active = request.active(); }
  private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
  private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
  private String favoriteCharacter(Film film, String character) {
   String value = blankToNull(character);
   if (value == null) return null;
   if (film.tmdbId == null || tmdb.details(film.tmdbId).cast().stream().noneMatch(member -> value.equals(member.character()))) throw badRequest("Elegí un personaje del reparto de TMDB");
   return value;
  }
   private void refreshWatchSummary(Film film) { List<FilmView> values = views.findByFilmIdOrderByWatchedOnDescIdDesc(film.id); film.watchedCount = values.size(); film.lastWatchedOn = values.isEmpty() ? null : values.getFirst().watchedOn; film.updatedAt = Instant.now(); films.save(film); }
  private static void validateViewMoment(FilmViewRequest request) { if (request.watchedOn().isAfter(RosarioClock.today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una vista no puede quedar en el futuro"); }
  private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
}
