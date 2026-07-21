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
record FilmReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment, LocalDate watchedOn, Map<@NotBlank @Pattern(regexp = "[a-z_]{1,80}") String, @NotNull @Min(1) @Max(5) Short> metrics) {}
record FilmGenreOptionRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String emoji) {}
record FilmGenreOptionDto(Long id, String name, String emoji) {}
record FilmReviewDto(Long id, String author, short rating, String comment, LocalDate watchedOn, Map<String, Short> metrics) {}
record FilmDto(Long id, Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterUrl, String thumbnailUrl, Integer posterWidth, Integer posterHeight, List<String> genres, PlatformDto platform, int watchedCount, LocalDate lastWatchedOn, String author, List<FilmReviewDto> reviews, Instant createdAt, TmdbMovieDto tmdb) {}

@RestController
@RequestMapping("/api")
public class FilmApi {
 private final Films films;
 private final FilmReviews reviews;
 private final WatchPlatforms platforms;
  private final FilmPhotos filmPhotos;
  private final FilmGenreOptions genreOptions;
  private final PhotoStorage storage;
  private final TmdbClient tmdb;

  public FilmApi(Films films, FilmReviews reviews, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb) {
   this.films = films; this.reviews = reviews; this.platforms = platforms; this.filmPhotos = filmPhotos; this.genreOptions = genreOptions; this.storage = storage; this.tmdb = tmdb;
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
    .sorted(Comparator.comparing((Film film) -> film.updatedAt).reversed())
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
   apply(film, request); film.createdBy = author; film.createdAt = film.updatedAt = Instant.now();
   return film(films.save(film), true);
  }
  @PutMapping("/films/{id}") FilmDto update(@PathVariable Long id, @RequestBody @Valid FilmRequest request) {
   Film film = findFilm(id); assertAvailableTmdbId(request.tmdbId(), id); apply(film, request); film.updatedAt = Instant.now(); return film(films.save(film), true);
 }
 @DeleteMapping("/films/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) { films.delete(findFilm(id)); }
 @PostMapping(value = "/films/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional FilmDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws java.io.IOException {
  Film film = owned(findFilm(id), user); filmPhotos.findByFilmId(id).ifPresent(filmPhotos::delete); filmPhotos.flush(); filmPhotos.save(storage.store(film, file)); return film(film);
 }

  @PostMapping("/films/{id}/reviews") @Transactional FilmReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
   Film film = findFilm(id);
   LocalDate watchedOn = request.watchedOn() == null ? LocalDate.now() : request.watchedOn();
   FilmReview review = new FilmReview(); review.film = film; review.author = author; review.createdAt = Instant.now();
    review.rating = request.rating(); review.comment = emptyToNull(request.comment()); review.watchedOn = watchedOn; if (request.metrics() != null) review.metrics.putAll(request.metrics()); review.updatedAt = Instant.now();
   film.watchedCount++; film.lastWatchedOn = watchedOn;
   film.updatedAt = Instant.now(); films.save(film);
   return review(reviews.save(review));
  }

  @PutMapping("/films/{filmId}/reviews/{reviewId}") @Transactional FilmReviewDto updateReview(@PathVariable Long filmId, @PathVariable Long reviewId, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
    FilmReview review = reviews.findByIdAndFilmId(reviewId, filmId).orElseThrow(() -> notFound("Reseña"));
    if (!review.author.id.equals(author.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
   review.rating = request.rating(); review.comment = emptyToNull(request.comment()); review.watchedOn = request.watchedOn() == null ? review.watchedOn : request.watchedOn(); review.metrics.clear(); if (request.metrics() != null) review.metrics.putAll(request.metrics()); review.updatedAt = Instant.now();
   return review(reviews.save(review));
  }

  private Film findFilm(Long id) { return films.findDetailedById(id).orElseThrow(() -> notFound("Película")); }
  private void assertAvailableTmdbId(Long tmdbId, Long currentId) {
   if (tmdbId == null) return;
   films.findByTmdbId(tmdbId).filter(existing -> !existing.id.equals(currentId)).ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa película ya está en WhichFilm"); });
  }
  private FilmDto film(Film film) { return film(film, true); }
  private FilmDto film(Film film, boolean detailedTmdb) {
    List<FilmReviewDto> filmReviews = reviews.findByFilmIdOrderByWatchedOnDescIdDesc(film.id).stream().map(FilmApi::review).toList();
   FilmPhoto photo = filmPhotos.findByFilmId(film.id).orElse(null);
   TmdbMovieDto catalog = catalog(film.tmdbId, detailedTmdb);
    return new FilmDto(film.id, film.tmdbId, film.title, film.originalTitle, film.synopsis, film.releaseDate, photo == null ? posterUrl(film.posterPath) : photoUrl(film.id, false), photo == null ? null : photoUrl(film.id, true), photo == null ? null : photo.width, photo == null ? null : photo.height, film.genres.stream().map(value -> value.name).sorted(String.CASE_INSENSITIVE_ORDER).toList(), film.platform == null ? null : platform(film.platform), film.watchedCount, film.lastWatchedOn, film.createdBy.username, filmReviews, film.createdAt, catalog);
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
   if (request.watchedOn() != null) film.lastWatchedOn = request.watchedOn();
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
  private static String photoUrl(Long filmId, boolean thumbnail) { return "/films/" + filmId + "/photo" + (thumbnail ? "?thumbnail=true" : ""); }
  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
  private static PlatformDto platform(WatchPlatform value) { return new PlatformDto(value.id, value.name, value.icon, value.active); }
  private static FilmGenreOptionDto genre(FilmGenreOption value) { return new FilmGenreOptionDto(value.id, value.name, value.emoji); }
  private static void apply(FilmGenreOption value, FilmGenreOptionRequest request) { value.name = request.name().trim(); value.emoji = request.emoji().trim(); }
   private static FilmReviewDto review(FilmReview value) { return new FilmReviewDto(value.id, value.author.username, value.rating, value.comment, value.watchedOn, Map.copyOf(value.metrics)); }
 private static void apply(WatchPlatform value, PlatformRequest request) { value.name = request.name().trim(); value.icon = request.icon().trim(); value.active = request.active(); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
 private static Film owned(Film film, User user) { if (!film.createdBy.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN); return film; }
}
