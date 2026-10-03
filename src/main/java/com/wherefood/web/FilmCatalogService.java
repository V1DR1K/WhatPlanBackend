package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Film;
import com.wherefood.domain.FilmGenreOption;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.FilmGenreOptions;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.WatchPlatforms;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Coordinates couple-scoped writes to the film aggregate. */
@Service
@Transactional(readOnly = true)
public class FilmCatalogService {
    private final Films films;
    private final FilmGenreOptions genreOptions;
    private final WatchPlatforms platforms;
    private final TmdbClient tmdb;
    private final CoupleAuthorizationService authorization;
    private final ZoneSettingsService zoneSettings;
    private final com.wherefood.journey.JourneyService journey;

    public FilmCatalogService(Films films, FilmGenreOptions genreOptions, WatchPlatforms platforms,
            TmdbClient tmdb, CoupleAuthorizationService authorization) {
        this(films, genreOptions, platforms, tmdb, authorization, null);
    }

    public FilmCatalogService(Films films, FilmGenreOptions genreOptions, WatchPlatforms platforms,
            TmdbClient tmdb, CoupleAuthorizationService authorization, ZoneSettingsService zoneSettings) { this(films, genreOptions, platforms, tmdb, authorization, zoneSettings, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public FilmCatalogService(Films films, FilmGenreOptions genreOptions, WatchPlatforms platforms,
            TmdbClient tmdb, CoupleAuthorizationService authorization, ZoneSettingsService zoneSettings, com.wherefood.journey.JourneyService journey) {
        this.films = films;
        this.genreOptions = genreOptions;
        this.platforms = platforms;
        this.tmdb = tmdb;
        this.authorization = authorization;
        this.zoneSettings = zoneSettings;
        this.journey = journey;
    }

    @Transactional
    public Film create(FilmRequest request, User actor) {
        authorization.requireActiveMember(actor);
        ensureAvailableTmdbId(request.tmdbId(), null);
        Film film = new Film();
        apply(film, request);
        film.createdBy = film.updatedBy = actor;
        film.createdAt = film.updatedAt = Instant.now();
        Film saved = films.save(film);
        if (journey != null) journey.pending("FILM", saved.id, request.stageId());
        return saved;
    }

    @Transactional
    public Film update(Long filmId, FilmRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Film film = findFilm(filmId);
        ensureAvailableTmdbId(request.tmdbId(), filmId);
        apply(film, request);
        film.updatedBy = actor;
        film.updatedAt = Instant.now();
        return films.save(film);
    }

    @Transactional
    public void delete(Long filmId, User actor) {
        authorization.requireActiveMember(actor);
        films.delete(findFilm(filmId));
    }

    private void ensureAvailableTmdbId(Long tmdbId, Long currentFilmId) {
        if (tmdbId == null) return;
        films.findByTmdbIdAndCoupleId(tmdbId, CoupleContext.current())
                .filter(existing -> !existing.id.equals(currentFilmId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Esa película ya está en WhichFilm");
                });
    }

    private Film findFilm(Long filmId) {
        return films.findDetailedByIdAndCoupleId(filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Película no encontrada"));
    }

    private void apply(Film film, FilmRequest request) {
        if (request.zoneId() != null) {
            if (zoneSettings != null) zoneSettings.requireActive(request.zoneId());
            film.zoneId = request.zoneId();
        } else if (film.zoneId == null) {
            if (zoneSettings != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una Zona para el registro");
            film.zoneId = 1L;
        }
        if (request.tmdbId() == null) {
            if (request.title() == null || request.title().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Indicá el título de la película");
            }
            film.tmdbId = null;
            film.title = request.title().trim();
            film.originalTitle = blankToNull(request.originalTitle());
            film.synopsis = blankToNull(request.synopsis());
            film.releaseDate = request.releaseDate();
            film.posterPath = blankToNull(request.posterPath());
            Set<String> names = request.genres() == null ? Set.of()
                    : request.genres().stream().map(String::trim).filter(value -> !value.isBlank())
                            .limit(12).collect(Collectors.toCollection(LinkedHashSet::new));
            List<FilmGenreOption> selected = names.isEmpty() ? List.of()
                    : genreOptions.findAllByNameIn(names);
            Set<String> previouslyAssigned = film.genres.stream().map(genre -> genre.name).collect(Collectors.toSet());
            if (selected.size() != names.size()
                    || selected.stream().anyMatch(genre -> !genre.active && !previouslyAssigned.contains(genre.name))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Género no encontrado");
            }
            film.genres.clear();
            film.genres.addAll(selected);
        } else {
            TmdbMovieDto source = tmdb.details(request.tmdbId());
            if (source.title() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "TMDB no devolvió un título para esa película");
            }
            film.tmdbId = source.tmdbId();
            film.title = source.title();
            film.originalTitle = null;
            film.synopsis = null;
            film.releaseDate = null;
            film.posterPath = null;
            film.genres.clear();
        }
        film.platform = request.platformId() == null ? null
                : platforms.findById(request.platformId())
                        .filter(platform -> platform.active || (film.platform != null
                                && request.platformId().equals(film.platform.id)))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Plataforma no encontrada"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
