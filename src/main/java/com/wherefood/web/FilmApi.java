package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record PlatformRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String icon, boolean active) {}
record PlatformDto(Long id, String name, String icon, boolean active) {}
record FilmRequest(@NotBlank @Size(max = 200) String title, Long tmdbId, @Size(max = 200) String originalTitle, @Size(max = 3000) String synopsis, LocalDate releaseDate, @Size(max = 300) String posterPath, List<@Size(max = 80) String> genres, Long platformId) {}
record FilmReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment, LocalDate watchedOn) {}
record WatchCountRequest(@Min(-100) @Max(100) int delta, LocalDate watchedOn) {}
record FilmReviewDto(String author, short rating, String comment, LocalDate watchedOn) {}
record FilmDto(Long id, Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterUrl, List<String> genres, PlatformDto platform, int watchedCount, LocalDate lastWatchedOn, String author, List<FilmReviewDto> reviews, Instant createdAt) {}

@RestController
@RequestMapping("/api")
public class FilmApi {
 private final Films films;
 private final FilmReviews reviews;
 private final WatchPlatforms platforms;
 private final TmdbClient tmdb;

 public FilmApi(Films films, FilmReviews reviews, WatchPlatforms platforms, TmdbClient tmdb) {
  this.films = films;
  this.reviews = reviews;
  this.platforms = platforms;
  this.tmdb = tmdb;
 }

 @GetMapping("/film-catalog/search") List<CatalogFilmDto> searchCatalog(@RequestParam String query) { return tmdb.search(query); }
 @GetMapping("/film-catalog/{tmdbId}") CatalogFilmDto catalogDetails(@PathVariable long tmdbId) { return tmdb.details(tmdbId); }

 @GetMapping("/watch-platforms") List<PlatformDto> activePlatforms() { return platforms.findByActiveTrueOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @GetMapping("/watch-platforms/all") @PreAuthorize("hasRole('ADMIN')") List<PlatformDto> allPlatforms() { return platforms.findAllByOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @PostMapping("/watch-platforms") @PreAuthorize("hasRole('ADMIN')") PlatformDto addPlatform(@RequestBody @Valid PlatformRequest request) { WatchPlatform value = new WatchPlatform(); apply(value, request); value.createdAt = Instant.now(); return platform(platforms.save(value)); }
 @PutMapping("/watch-platforms/{id}") @PreAuthorize("hasRole('ADMIN')") PlatformDto updatePlatform(@PathVariable Long id, @RequestBody @Valid PlatformRequest request) { WatchPlatform value = platforms.findById(id).orElseThrow(() -> notFound("Plataforma")); apply(value, request); return platform(platforms.save(value)); }

 @GetMapping("/films") List<FilmDto> list(@RequestParam(required = false) String genre, @RequestParam(required = false) Long platformId, @RequestParam(required = false) Boolean watched) {
  return films.findAll().stream()
    .filter(film -> platformId == null || (film.platform != null && film.platform.id.equals(platformId)))
    .filter(film -> watched == null || watched == (film.watchedCount > 0))
    .filter(film -> genre == null || genre.isBlank() || film.genres.stream().anyMatch(value -> value.equalsIgnoreCase(genre)))
    .sorted(Comparator.comparing((Film film) -> film.updatedAt).reversed())
    .map(this::film).toList();
 }

 @GetMapping("/films/{id}") FilmDto get(@PathVariable Long id) { return film(findFilm(id)); }
 @PostMapping("/films") @ResponseStatus(HttpStatus.CREATED) FilmDto add(@RequestBody @Valid FilmRequest request, @AuthenticationPrincipal User author) {
  assertAvailableTmdbId(request.tmdbId(), null);
  Film film = new Film();
  apply(film, request); film.createdBy = author; film.createdAt = film.updatedAt = Instant.now();
  return film(films.save(film));
 }
 @PutMapping("/films/{id}") FilmDto update(@PathVariable Long id, @RequestBody @Valid FilmRequest request) {
  Film film = findFilm(id); assertAvailableTmdbId(request.tmdbId(), id); apply(film, request); film.updatedAt = Instant.now(); return film(films.save(film));
 }
 @DeleteMapping("/films/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) { films.delete(findFilm(id)); }

 @PatchMapping("/films/{id}/watch-count") @Transactional FilmDto adjustWatchCount(@PathVariable Long id, @RequestBody @Valid WatchCountRequest request) {
  if (request.delta() == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá un cambio de vistas");
  Film film = findFilm(id);
  film.watchedCount = Math.max(0, film.watchedCount + request.delta());
  if (request.delta() > 0) film.lastWatchedOn = request.watchedOn() == null ? LocalDate.now() : request.watchedOn();
  film.updatedAt = Instant.now();
  return film(films.save(film));
 }

 @PutMapping("/films/{id}/review") @Transactional FilmReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
  Film film = findFilm(id);
  FilmReview review = reviews.findByFilmIdAndAuthorId(id, author.id).orElseGet(() -> { FilmReview value = new FilmReview(); value.film = film; value.author = author; value.createdAt = Instant.now(); return value; });
  review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.watchedOn = request.watchedOn(); review.updatedAt = Instant.now();
  film.updatedAt = Instant.now(); films.save(film);
  return review(reviews.save(review));
 }

 private Film findFilm(Long id) { return films.findDetailedById(id).orElseThrow(() -> notFound("Película")); }
 private void assertAvailableTmdbId(Long tmdbId, Long currentId) {
  if (tmdbId == null) return;
  films.findByTmdbId(tmdbId).filter(existing -> !existing.id.equals(currentId)).ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa película ya está en WhichFilm"); });
 }
 private FilmDto film(Film film) {
  List<FilmReviewDto> filmReviews = reviews.findByFilmIdOrderByAuthorUsername(film.id).stream().map(FilmApi::review).toList();
  return new FilmDto(film.id, film.tmdbId, film.title, film.originalTitle, film.synopsis, film.releaseDate, posterUrl(film.posterPath), film.genres.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), film.platform == null ? null : platform(film.platform), film.watchedCount, film.lastWatchedOn, film.createdBy.username, filmReviews, film.createdAt);
 }
 private void apply(Film film, FilmRequest request) {
  film.title = request.title().trim(); film.tmdbId = request.tmdbId(); film.originalTitle = blankToNull(request.originalTitle()); film.synopsis = blankToNull(request.synopsis()); film.releaseDate = request.releaseDate(); film.posterPath = blankToNull(request.posterPath());
  film.platform = request.platformId() == null ? null : platforms.findById(request.platformId()).orElseThrow(() -> notFound("Plataforma"));
  film.genres.clear(); if (request.genres() != null) request.genres().stream().map(String::trim).filter(value -> !value.isBlank()).limit(12).forEach(film.genres::add);
 }
 private static String posterUrl(String posterPath) { if (posterPath == null) return null; return posterPath.startsWith("http") ? posterPath : "https://image.tmdb.org/t/p/w500" + posterPath; }
 private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static PlatformDto platform(WatchPlatform value) { return new PlatformDto(value.id, value.name, value.icon, value.active); }
 private static FilmReviewDto review(FilmReview value) { return new FilmReviewDto(value.author.username, value.rating, value.comment, value.watchedOn); }
 private static void apply(WatchPlatform value, PlatformRequest request) { value.name = request.name().trim(); value.icon = request.icon().trim(); value.active = request.active(); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
}
