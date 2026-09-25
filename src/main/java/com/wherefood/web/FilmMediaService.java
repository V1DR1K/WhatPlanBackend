package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Film;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.FilmPhotos;
import com.wherefood.repo.Repositories.Films;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Couple-scoped film photo replacement. */
@Service
@Transactional(readOnly = true)
public class FilmMediaService {
    private final Films films;
    private final FilmPhotos photos;
    private final PhotoStorage storage;
    private final CoupleAuthorizationService authorization;

    public FilmMediaService(Films films, FilmPhotos photos, PhotoStorage storage,
            CoupleAuthorizationService authorization) {
        this.films = films;
        this.photos = photos;
        this.storage = storage;
        this.authorization = authorization;
    }

    @Transactional
    public Film replacePhoto(Long filmId, MultipartFile file, User actor) throws IOException {
        authorization.requireActiveMember(actor);
        Film film = films.findDetailedByIdAndCoupleId(filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Película no encontrada"));
        photos.findByFilmIdAndCoupleId(filmId, CoupleContext.current()).ifPresent(photos::delete);
        photos.flush();
        film.updatedBy = actor;
        film.updatedAt = Instant.now();
        films.save(film);
        photos.save(storage.store(film, file));
        return film;
    }
}
