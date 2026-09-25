package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Film;
import com.wherefood.domain.FilmView;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.FilmViews;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns film-view mutations and the derived film watch summary in one transaction. */
@Service
@Transactional(readOnly = true)
public class FilmViewService {
    private final Films films;
    private final FilmViews views;
    private final CoupleAuthorizationService authorization;

    public FilmViewService(Films films, FilmViews views, CoupleAuthorizationService authorization) {
        this.films = films;
        this.views = views;
        this.authorization = authorization;
    }

    @Transactional
    public FilmView create(Long filmId, FilmViewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Film film = findFilm(filmId);
        validateDate(request.watchedOn());
        if (views.findByFilmIdAndWatchedOnAndCoupleId(film.id, request.watchedOn(), CoupleContext.current()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraron una vista para esa fecha");
        }
        FilmView view = new FilmView();
        view.film = film;
        view.createdBy = view.updatedBy = actor;
        view.watchedOn = request.watchedOn();
        view.createdAt = Instant.now();
        film.updatedBy = actor;
        FilmView saved = views.save(view);
        refreshSummary(film);
        return saved;
    }

    @Transactional
    public FilmView update(Long filmId, Long viewId, FilmViewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        FilmView view = findView(filmId, viewId);
        validateDate(request.watchedOn());
        views.findByFilmIdAndWatchedOnAndCoupleId(filmId, request.watchedOn(), CoupleContext.current())
                .filter(other -> !other.id.equals(view.id))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya registraron una vista para esa fecha"); });
        view.watchedOn = request.watchedOn();
        view.updatedBy = actor;
        view.film.updatedBy = actor;
        FilmView saved = views.save(view);
        refreshSummary(saved.film);
        return saved;
    }

    @Transactional
    public void delete(Long filmId, Long viewId, User actor) {
        authorization.requireActiveMember(actor);
        FilmView view = findView(filmId, viewId);
        Film film = view.film;
        film.updatedBy = actor;
        views.delete(view);
        views.flush();
        refreshSummary(film);
    }

    private Film findFilm(Long filmId) {
        return films.findDetailedByIdAndCoupleId(filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada"));
    }

    private FilmView findView(Long filmId, Long viewId) {
        findFilm(filmId);
        return views.findByIdAndFilmIdAndCoupleId(viewId, filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vista no encontrada"));
    }

    private void refreshSummary(Film film) {
        List<FilmView> values = views.findByFilmIdAndCoupleIdOrderByWatchedOnDescIdDesc(film.id, CoupleContext.current());
        film.watchedCount = values.size();
        film.lastWatchedOn = values.isEmpty() ? null : values.getFirst().watchedOn;
        film.updatedAt = Instant.now();
        films.save(film);
    }

    private static void validateDate(LocalDate watchedOn) {
        if (watchedOn.isAfter(RosarioClock.today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una vista no puede quedar en el futuro");
        }
    }
}
