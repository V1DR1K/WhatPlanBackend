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
import com.wherefood.validation.SafeHttpUrl;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.config.CoupleContext;
import org.springframework.validation.annotation.Validated;

record PlatformRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String icon, boolean active) {}
record PlatformDto(Long id, String name, String icon, boolean active) {}
record FilmRequest(@Positive Long tmdbId, @Size(max = 200) String title, @Size(max = 200) String originalTitle, @Size(max = 3000) String synopsis, LocalDate releaseDate, @Size(max = 1000) @SafeHttpUrl String posterPath, LocalDate watchedOn, @Size(max = 12) List<@Size(max = 80) String> genres, @Positive Long platformId, @Positive Long zoneId, UUID stageId) {
 FilmRequest(Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterPath, LocalDate watchedOn, List<String> genres, Long platformId) {
  this(tmdbId, title, originalTitle, synopsis, releaseDate, posterPath, watchedOn, genres, platformId, null, null);
 }
 FilmRequest(Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterPath, LocalDate watchedOn, List<String> genres, Long platformId, Long zoneId) { this(tmdbId, title, originalTitle, synopsis, releaseDate, posterPath, watchedOn, genres, platformId, zoneId, null); }
}
record FilmViewRequest(@NotNull LocalDate watchedOn, @Positive Long cityId, UUID stageId, UUID pointId) {
 FilmViewRequest(LocalDate watchedOn) { this(watchedOn,null,null,null); }
}
record FilmReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment, LocalDate watchedOn, @Size(max = 300) String favoriteCharacter, @Size(max = 20) Map<@NotBlank @Pattern(regexp = "[a-z_]{1,80}") String, @NotNull @Min(1) @Max(5) Short> metrics) {}
record FilmGenreOptionRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 20) String emoji, Boolean active) {
 FilmGenreOptionRequest(String name, String emoji) { this(name, emoji, null); }
}
record FilmGenreOptionDto(Long id, String name, String emoji, boolean active) {}
record FilmReviewDto(Long id, String author, short rating, String comment, LocalDate watchedOn, String favoriteCharacter, Map<String, Short> metrics) {}
record FilmViewDto(Long id, LocalDate watchedOn, String createdBy, String updatedBy, List<FilmReviewDto> reviews, Instant createdAt, Long cityId, UUID stageId) {}
 record FilmDto(Long id, Long zoneId, Long tmdbId, String title, String originalTitle, String synopsis, LocalDate releaseDate, String posterUrl, String thumbnailUrl, Integer posterWidth, Integer posterHeight, List<String> genres, PlatformDto platform, int watchedCount, LocalDate lastWatchedOn, String author, List<FilmReviewDto> reviews, List<FilmViewDto> views, Instant createdAt, Instant updatedAt, TmdbMovieDto tmdb) {}

@RestController
@Validated
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
  private final FilmReviewService reviewService;
  private final FilmViewService viewService;
  private final FilmCatalogService catalogService;
  private final FilmMediaService mediaService;
  private final FilmCatalogAdminService catalogAdminService;

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb) {
    this(films, reviews, views, platforms, filmPhotos, genreOptions, storage, tmdb,
            new FilmReviewService(reviews, new CoupleAuthorizationService(null), tmdb),
            new FilmViewService(films, views, new CoupleAuthorizationService(null)),
            new FilmCatalogService(films, genreOptions, platforms, tmdb,
                    new CoupleAuthorizationService(null)));
   }

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb, FilmReviewService reviewService) {
    this(films, reviews, views, platforms, filmPhotos, genreOptions, storage, tmdb, reviewService,
            new FilmViewService(films, views, new CoupleAuthorizationService(null)),
            new FilmCatalogService(films, genreOptions, platforms, tmdb,
                    new CoupleAuthorizationService(null)));
   }

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb, FilmReviewService reviewService, FilmViewService viewService) {
    this(films, reviews, views, platforms, filmPhotos, genreOptions, storage, tmdb, reviewService, viewService,
            new FilmCatalogService(films, genreOptions, platforms, tmdb, new CoupleAuthorizationService(null)));
   }

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms, FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb, FilmReviewService reviewService, FilmViewService viewService, FilmCatalogService catalogService) {
    this(films, reviews, views, platforms, filmPhotos, genreOptions, storage, tmdb, reviewService,
            viewService, catalogService, new FilmMediaService(films, filmPhotos, storage,
                    new CoupleAuthorizationService(null)));
   }

   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms,
           FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb,
           FilmReviewService reviewService, FilmViewService viewService,
           FilmCatalogService catalogService, FilmMediaService mediaService) {
    this(films, reviews, views, platforms, filmPhotos, genreOptions, storage, tmdb, reviewService,
            viewService, catalogService, mediaService,
            new FilmCatalogAdminService(platforms, genreOptions));
   }

   @org.springframework.beans.factory.annotation.Autowired
   public FilmApi(Films films, FilmReviews reviews, FilmViews views, WatchPlatforms platforms,
           FilmPhotos filmPhotos, FilmGenreOptions genreOptions, PhotoStorage storage, TmdbClient tmdb,
           FilmReviewService reviewService, FilmViewService viewService,
           FilmCatalogService catalogService, FilmMediaService mediaService,
           FilmCatalogAdminService catalogAdminService) {
    this.films = films; this.reviews = reviews; this.views = views; this.platforms = platforms; this.filmPhotos = filmPhotos; this.genreOptions = genreOptions; this.storage = storage; this.tmdb = tmdb; this.reviewService = reviewService; this.viewService = viewService; this.catalogService = catalogService;
    this.mediaService = mediaService; this.catalogAdminService = catalogAdminService;
   }

   @GetMapping("/tmdb/movies") List<TmdbMovieDto> searchTmdb(@RequestParam @Size(max = 100) String query) { return tmdb.search(query); }
   @GetMapping("/tmdb/movies/{tmdbId}/recommendations") List<TmdbMovieDto> recommendations(@PathVariable @Positive long tmdbId) { return tmdb.recommendations(tmdbId); }
  @GetMapping("/watch-platforms") List<PlatformDto> activePlatforms() { return platforms.findByActiveTrueOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @GetMapping("/watch-platforms/all") @PreAuthorize("hasRole('ADMIN')") List<PlatformDto> allPlatforms() { return platforms.findAllByOrderByNameAsc().stream().map(FilmApi::platform).toList(); }
 @PostMapping("/watch-platforms") @PreAuthorize("hasRole('ADMIN')") PlatformDto addPlatform(@RequestBody @Valid PlatformRequest request) { return platform(catalogAdminService.createPlatform(request)); }
 @PutMapping("/watch-platforms/{id}") @PreAuthorize("hasRole('ADMIN')") PlatformDto updatePlatform(@PathVariable Long id, @RequestBody @Valid PlatformRequest request) { return platform(catalogAdminService.updatePlatform(id, request)); }
 @DeleteMapping("/watch-platforms/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlatform(@PathVariable Long id) { catalogAdminService.deletePlatform(id); }
  @GetMapping("/film-genres") List<FilmGenreOptionDto> genres() { return genreOptions.findByActiveTrueOrderByNameAsc().stream().map(FilmApi::genre).toList(); }
  @GetMapping("/film-genres/all") @PreAuthorize("hasRole('ADMIN')") List<FilmGenreOptionDto> allGenres() { return genreOptions.findAllByOrderByNameAsc().stream().map(FilmApi::genre).toList(); }
  @PostMapping("/film-genres") @PreAuthorize("hasRole('ADMIN')") FilmGenreOptionDto addGenre(@RequestBody @Valid FilmGenreOptionRequest request) { return genre(catalogAdminService.createGenre(request)); }
  @PutMapping("/film-genres/{id}") @PreAuthorize("hasRole('ADMIN')") FilmGenreOptionDto updateGenre(@PathVariable Long id, @RequestBody @Valid FilmGenreOptionRequest request) { return genre(catalogAdminService.updateGenre(id, request)); }
  @DeleteMapping("/film-genres/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteGenre(@PathVariable Long id) { catalogAdminService.deleteGenre(id); }

  @GetMapping("/films") Slice<FilmDto> list(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) String genre, @RequestParam(required = false) Long platformId, @RequestParam(required = false) Boolean watched, @RequestParam(required = false) String search, @RequestParam(required = false) String sort, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int size) {
   int limit = Math.max(1, Math.min(size, 30));
   long offset = cursor == null ? 0 : Math.max(0, cursor);
   if (offset > 1_000_000) throw badRequest("Cursor inválido");
   String normalizedSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
   String normalizedSort = sort == null ? "date-desc" : sort.trim().toLowerCase(Locale.ROOT);
   if (!Set.of("date", "date-desc", "date-asc", "rating", "rating-desc", "rating-asc").contains(normalizedSort)) {
    throw badRequest("Orden inválido");
   }
   String normalizedGenre = genre == null || genre.isBlank() ? null : genre.trim().toLowerCase(Locale.ROOT);
   List<Long> ids = films.findPageIdsByCoupleId(CoupleContext.current(), normalizedGenre, platformId,
           watched, normalizedSearch, normalizedSort, limit + 1, offset);
   Long next = ids.size() > limit ? offset + limit : null;
   List<Long> pageIds = ids.stream().limit(limit).toList();
   if (pageIds.isEmpty()) return new Slice<>(List.of(), null);
   Map<Long, Film> byId = films.findAllByIdInAndCoupleId(pageIds, CoupleContext.current()).stream()
           .collect(java.util.stream.Collectors.toMap(film -> film.id, film -> film));
   List<Film> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
   Map<Long, FilmPhotoMetadata> photosByFilm = filmPhotos(page);
   return new Slice<>(page.stream().map(film -> film(film, false, photosByFilm.get(film.id))).toList(), next);
   }

  Slice<FilmDto> list(String genre, Long platformId, Boolean watched, String search, String sort, Long cursor, int size) {
   return list(null, genre, platformId, watched, search, sort, cursor, size);
  }

  @GetMapping("/films/{id}") FilmDto get(@PathVariable Long id) { return film(findFilm(id), true); }
  @GetMapping(value = "/films/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   findFilm(id);
   FilmPhoto photo = filmPhotos.findByFilmIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
    return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping("/films") @ResponseStatus(HttpStatus.CREATED) FilmDto add(@RequestBody @Valid FilmRequest request, @AuthenticationPrincipal User author) {
   return film(catalogService.create(request, author), true);
  }
  @PutMapping("/films/{id}") FilmDto update(@PathVariable Long id, @RequestBody @Valid FilmRequest request, @AuthenticationPrincipal User author) {
    return film(catalogService.update(id, request, author), true);
  }
  @DeleteMapping("/films/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id, @AuthenticationPrincipal User author) { catalogService.delete(id, author); }
 @PostMapping(value = "/films/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) FilmDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws java.io.IOException {
   return film(mediaService.replacePhoto(id, file, user));
 }

   @PostMapping("/films/{id}/views") FilmViewDto addView(@PathVariable Long id, @RequestBody @Valid FilmViewRequest request, @AuthenticationPrincipal User author) {
     FilmView view = viewService.create(id, request, author); return view(view, List.of());
  }
  @PutMapping("/films/{filmId}/views/{viewId}") FilmViewDto updateView(@PathVariable Long filmId, @PathVariable Long viewId, @RequestBody @Valid FilmViewRequest request, @AuthenticationPrincipal User author) {
     FilmView saved = viewService.update(filmId, viewId, request, author); return view(saved, reviews.findByFilmIdAndCoupleIdOrderByViewWatchedOnDescIdDesc(filmId, CoupleContext.current()).stream().filter(review -> review.view.id.equals(saved.id)).map(FilmApi::review).toList());
  }
  @DeleteMapping("/films/{filmId}/views/{viewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteView(@PathVariable Long filmId, @PathVariable Long viewId, @AuthenticationPrincipal User author) { viewService.delete(filmId, viewId, author); }

 @PostMapping("/films/{filmId}/views/{viewId}/reviews") FilmReviewDto addReview(@PathVariable Long filmId, @PathVariable Long viewId, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.createForView(filmId, viewId, request, author));
 }

 @PostMapping("/films/{id}/reviews") FilmReviewDto saveLegacyReview(@PathVariable Long id, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
  return review(reviewService.saveLegacy(id, request, author));
 }

  @PutMapping("/films/{filmId}/reviews/{reviewId}") FilmReviewDto updateReview(@PathVariable Long filmId, @PathVariable Long reviewId, @RequestBody @Valid FilmReviewRequest request, @AuthenticationPrincipal User author) {
   return review(reviewService.update(filmId, reviewId, request, author));
  }
  @DeleteMapping("/films/{filmId}/reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long filmId, @PathVariable Long reviewId, @AuthenticationPrincipal User author) { reviewService.delete(filmId, reviewId, author); }

  private Film findFilm(Long id) { return films.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Película")); }
   private FilmDto film(Film film) { return film(film, true, filmPhotos(List.of(film)).get(film.id)); }
   private FilmDto film(Film film, boolean detailedTmdb) { return film(film, detailedTmdb, filmPhotos(List.of(film)).get(film.id)); }
   private FilmDto film(Film film, boolean detailedTmdb, PhotoMetadata photo) {
   List<FilmReview> reviewValues = reviews.findByFilmIdAndCoupleIdOrderByViewWatchedOnDescIdDesc(film.id, CoupleContext.current());
   Map<Long, String> reviewAuthors = reviews.authorsByFilmIdAndCoupleId(film.id, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   List<FilmReviewDto> filmReviews = reviewValues.stream().map(review -> review(review, reviewAuthors.get(review.id))).toList();
   Map<Long, List<FilmReviewDto>> reviewsByView = reviewValues.stream().collect(java.util.stream.Collectors.groupingBy(review -> review.view.id, java.util.stream.Collectors.mapping(review -> review(review, reviewAuthors.get(review.id)), java.util.stream.Collectors.toList())));
    List<FilmViewDto> filmViews = views.findByFilmIdAndCoupleIdOrderByWatchedOnDescIdDesc(film.id, CoupleContext.current()).stream().map(view -> view(view, reviewsByView.getOrDefault(view.id, List.of()))).toList();
    TmdbMovieDto catalog = catalog(film.tmdbId, detailedTmdb);
     String posterUrl = photo != null ? photoUrl(film.id, false, photo.getId()) : posterUrl(film.posterPath);
     String thumbnailUrl = photo != null ? photoUrl(film.id, true, photo.getId()) : null;
     Integer posterWidth = photo == null ? null : photo.getWidth();
     Integer posterHeight = photo == null ? null : photo.getHeight();
      return new FilmDto(film.id, film.zoneId, film.tmdbId, film.title, film.originalTitle, film.synopsis, film.releaseDate, posterUrl, thumbnailUrl, posterWidth, posterHeight, film.genres.stream().map(value -> value.name).sorted(String.CASE_INSENSITIVE_ORDER).toList(), film.platform == null ? null : platform(film.platform), film.watchedCount, film.lastWatchedOn, film.createdBy.username, filmReviews, filmViews, film.createdAt, film.updatedAt, catalog);
  }
   private Map<Long, FilmPhotoMetadata> filmPhotos(Collection<Film> values) {
    if (values.isEmpty() || filmPhotos == null) return Map.of();
    return filmPhotos.metadataByFilmIdInAndCoupleId(values.stream().map(film -> film.id).toList(), CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(FilmPhotoMetadata::getFilmId, photo -> photo));
   }
  private TmdbMovieDto catalog(Long tmdbId, boolean detailed) {
   if (tmdbId == null) return null;
   try { return detailed ? tmdb.details(tmdbId) : tmdb.summary(tmdbId); }
   catch (ResponseStatusException ignored) { return null; }
  }
  private static String posterUrl(String posterPath) { return posterPath; }
    private static String photoUrl(Long filmId, boolean thumbnail, Long photoId) { return "/films/" + filmId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static PlatformDto platform(WatchPlatform value) { return new PlatformDto(value.id, value.name, value.icon, value.active); }
  private static FilmGenreOptionDto genre(FilmGenreOption value) { return new FilmGenreOptionDto(value.id, value.name, value.emoji, value.active); }
   private static FilmViewDto view(FilmView value, List<FilmReviewDto> reviews) { return new FilmViewDto(value.id, value.watchedOn, value.createdBy.username, value.updatedBy == null ? value.createdBy.username : value.updatedBy.username, reviews, value.createdAt, value.cityId, value.stageId); }
   private static FilmReviewDto review(FilmReview value) { return review(value, value.author.username); }
   private static FilmReviewDto review(FilmReview value, String author) { return new FilmReviewDto(value.id, author, value.rating, value.comment, value.view.watchedOn, value.favoriteCharacter, Map.copyOf(value.metrics)); }
  private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
  private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
  private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
}
