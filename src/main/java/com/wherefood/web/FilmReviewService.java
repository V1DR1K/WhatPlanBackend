package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.FilmReview;
import com.wherefood.domain.Film;
import com.wherefood.domain.FilmView;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.FilmViews;
import com.wherefood.repo.Repositories.FilmReviews;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

/** Owns authorization and atomic writes for personal film reviews. */
@Service
@Transactional(readOnly = true)
public class FilmReviewService {
    private final FilmReviews reviews;
    private final Films films;
    private final FilmViews views;
    private final CoupleAuthorizationService authorization;
    private final FilmViewService viewService;
    private final TmdbClient tmdb;

    public FilmReviewService(FilmReviews reviews, CoupleAuthorizationService authorization, TmdbClient tmdb) {
        this(reviews, null, null, authorization, null, tmdb);
    }

    @Autowired
    public FilmReviewService(FilmReviews reviews, Films films, FilmViews views,
            CoupleAuthorizationService authorization, FilmViewService viewService, TmdbClient tmdb) {
        this.reviews = reviews;
        this.films = films;
        this.views = views;
        this.authorization = authorization;
        this.viewService = viewService;
        this.tmdb = tmdb;
    }

    @Transactional
    public FilmReview createForView(Long filmId, Long viewId, FilmReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Film film = findFilm(filmId);
        FilmView view = views.findByIdAndFilmIdAndCoupleId(viewId, filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vista no encontrada"));
        if (reviews.existsByViewIdAndAuthorIdAndCoupleId(view.id, actor.id, CoupleContext.current())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya dejaste tu reseña para esta vista");
        }
        return create(film, view, request, actor);
    }

    @Transactional
    public FilmReview saveLegacy(Long filmId, FilmReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Film film = findFilm(filmId);
        java.time.LocalDate watchedOn = request.watchedOn() == null ? RosarioClock.today() : request.watchedOn();
        FilmView view = views.findByFilmIdAndWatchedOnAndCoupleId(filmId, watchedOn, CoupleContext.current())
                .orElseGet(() -> viewService.create(filmId, new FilmViewRequest(watchedOn), actor));
        if (reviews.existsByViewIdAndAuthorIdAndCoupleId(view.id, actor.id, CoupleContext.current())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya dejaste tu reseña para esta vista");
        }
        return create(film, view, request, actor);
    }

    @Transactional
    public FilmReview update(Long filmId, Long reviewId, FilmReviewRequest request, User actor) {
        FilmReview review = reviews.findByIdAndFilmIdAndCoupleId(reviewId, filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        review.rating = request.rating();
        review.comment = emptyToNull(request.comment());
        review.favoriteCharacter = favoriteCharacter(review, request.favoriteCharacter());
        review.metrics.clear();
        if (request.metrics() != null) review.metrics.putAll(request.metrics());
        review.updatedBy = actor;
        review.updatedAt = Instant.now();
        return reviews.save(review);
    }

    @Transactional
    public void delete(Long filmId, Long reviewId, User actor) {
        FilmReview review = reviews.findByIdAndFilmIdAndCoupleId(reviewId, filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        reviews.delete(review);
    }

    private String favoriteCharacter(FilmReview review, String character) {
        String value = character == null || character.isBlank() ? null : character.trim();
        if (value == null) return null;
        if (review.film.tmdbId == null || tmdb == null
                || tmdb.details(review.film.tmdbId).cast().stream().noneMatch(member -> value.equals(member.character()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí un personaje del reparto de TMDB");
        }
        return value;
    }

    private FilmReview create(Film film, FilmView view, FilmReviewRequest request, User actor) {
        FilmReview review = new FilmReview();
        review.film = film;
        review.view = view;
        review.author = review.updatedBy = actor;
        review.createdAt = Instant.now();
        review.rating = request.rating();
        review.comment = emptyToNull(request.comment());
        review.favoriteCharacter = favoriteCharacter(review, request.favoriteCharacter());
        if (request.metrics() != null) review.metrics.putAll(request.metrics());
        review.updatedAt = Instant.now();
        film.updatedBy = actor;
        film.updatedAt = Instant.now();
        films.save(film);
        return reviews.save(review);
    }

    private Film findFilm(Long filmId) {
        return films.findDetailedByIdAndCoupleId(filmId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Película no encontrada"));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
