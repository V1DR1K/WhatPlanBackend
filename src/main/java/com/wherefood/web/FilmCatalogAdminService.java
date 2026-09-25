package com.wherefood.web;

import com.wherefood.domain.FilmGenreOption;
import com.wherefood.domain.WatchPlatform;
import com.wherefood.repo.Repositories.FilmGenreOptions;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.WatchPlatforms;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only management of the global film taxonomy and platforms. */
@Service
@Transactional(readOnly = true)
public class FilmCatalogAdminService {
    private final WatchPlatforms platforms;
    private final FilmGenreOptions genres;
    private final Films films;

    public FilmCatalogAdminService(WatchPlatforms platforms, FilmGenreOptions genres, Films films) {
        this.platforms = platforms;
        this.genres = genres;
        this.films = films;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WatchPlatform createPlatform(PlatformRequest request) {
        WatchPlatform value = new WatchPlatform();
        apply(value, request);
        value.createdAt = Instant.now();
        return platforms.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WatchPlatform updatePlatform(Long id, PlatformRequest request) {
        WatchPlatform value = platforms.findById(id).orElseThrow(() -> notFound("Plataforma"));
        apply(value, request);
        return platforms.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePlatform(Long id) {
        WatchPlatform value = platforms.findById(id).orElseThrow(() -> notFound("Plataforma"));
        if (films.existsByPlatformId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés borrar una plataforma usada por películas");
        }
        platforms.delete(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public FilmGenreOption createGenre(FilmGenreOptionRequest request) {
        FilmGenreOption value = new FilmGenreOption();
        apply(value, request);
        value.createdAt = Instant.now();
        return genres.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public FilmGenreOption updateGenre(Long id, FilmGenreOptionRequest request) {
        FilmGenreOption value = genres.findById(id).orElseThrow(() -> notFound("Género"));
        apply(value, request);
        return genres.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteGenre(Long id) {
        genres.delete(genres.findById(id).orElseThrow(() -> notFound("Género")));
    }

    private static void apply(WatchPlatform value, PlatformRequest request) {
        value.name = request.name().trim();
        value.icon = request.icon().trim();
        value.active = request.active();
    }

    private static void apply(FilmGenreOption value, FilmGenreOptionRequest request) {
        value.name = request.name().trim();
        value.emoji = request.emoji().trim();
    }

    private static ResponseStatusException notFound(String type) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado");
    }
}
