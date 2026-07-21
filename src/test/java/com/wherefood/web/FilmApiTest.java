package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Film;
import com.wherefood.domain.FilmReview;
import com.wherefood.domain.FilmView;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.FilmReviews;
import com.wherefood.repo.Repositories.FilmViews;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FilmApiTest {
  @Test
  void registersAViewWithoutCreatingAReview() {
    Films films = mock(Films.class);
    FilmReviews reviews = mock(FilmReviews.class);
    FilmViews views = mock(FilmViews.class);
    User tomas = new User();
    tomas.id = 7L;
    tomas.username = "tomas";
    Film film = new Film();
    film.id = 42L;
    when(films.findDetailedById(42L)).thenReturn(Optional.of(film));
    when(views.findByFilmIdAndWatchedOn(42L, LocalDate.of(2026, 7, 19))).thenReturn(Optional.empty());
    when(views.save(any(FilmView.class))).thenAnswer(invocation -> { FilmView value = invocation.getArgument(0); value.id = 88L; return value; });

    FilmViewDto result = new FilmApi(films, reviews, views, null, null, null, null, null).addView(
      42L,
      new FilmViewRequest(LocalDate.of(2026, 7, 19)),
      tomas
    );

    assertEquals(88L, result.id());
    assertEquals(LocalDate.of(2026, 7, 19), result.watchedOn());
    assertEquals(1, film.watchedCount);
    verify(views).save(any(FilmView.class));
  }

  @Test
  void letsEitherUserReviewTheSameViewWithoutIncreasingTheCount() {
    Films films = mock(Films.class);
    FilmReviews reviews = mock(FilmReviews.class);
    FilmViews views = mock(FilmViews.class);
    User avril = new User();
    avril.id = 6L;
    avril.username = "avril";
    Film film = new Film();
    film.id = 42L;
    film.watchedCount = 1;
    FilmView view = new FilmView();
    view.id = 88L;
    view.film = film;
    view.watchedOn = LocalDate.of(2026, 7, 19);
    when(films.findDetailedById(42L)).thenReturn(Optional.of(film));
    when(views.findByIdAndFilmId(88L, 42L)).thenReturn(Optional.of(view));
    when(reviews.existsByViewIdAndAuthorId(88L, 6L)).thenReturn(false);
    when(reviews.save(any(FilmReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

    FilmReviewDto result = new FilmApi(films, reviews, views, null, null, null, null, null).addReview(
      42L,
      88L,
      new FilmReviewRequest((short) 5, "La volvería a ver", null, Map.of("story", (short) 4)),
      avril
    );

    assertEquals("avril", result.author());
    assertEquals(LocalDate.of(2026, 7, 19), result.watchedOn());
    assertEquals(1, film.watchedCount);
    verify(reviews).save(any(FilmReview.class));
  }

  @Test
  void updatesReviewWithoutReadingLazyFilmId() {
    FilmReviews reviews = mock(FilmReviews.class);
    User author = new User();
    author.id = 7L;
    author.username = "tomas";
    FilmReview review = new FilmReview();
    review.film = new Film();
    review.view = new FilmView();
    review.view.watchedOn = LocalDate.of(2026, 7, 17);
    review.author = author;
    review.rating = 3;
    review.metrics.put("story", (short) 3);
    when(reviews.findByIdAndFilmId(99L, 42L)).thenReturn(Optional.of(review));
    when(reviews.save(review)).thenReturn(review);

    FilmReviewDto result = new FilmApi(null, reviews, null, null, null, null, null, null).updateReview(
      42L,
      99L,
      new FilmReviewRequest((short) 5, "Mejor de lo que recordaba", LocalDate.of(2026, 7, 18), Map.of("story", (short) 4)),
      author
    );

    verify(reviews).findByIdAndFilmId(99L, 42L);
    assertEquals(5, result.rating());
    assertEquals(LocalDate.of(2026, 7, 17), result.watchedOn());
    assertEquals(Map.of("story", (short) 4), result.metrics());
  }
}
