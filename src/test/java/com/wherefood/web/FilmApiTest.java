package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Film;
import com.wherefood.domain.FilmReview;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.FilmReviews;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FilmApiTest {
  @Test
  void updatesReviewWithoutReadingLazyFilmId() {
    FilmReviews reviews = mock(FilmReviews.class);
    User author = new User();
    author.id = 7L;
    author.username = "tomas";
    FilmReview review = new FilmReview();
    review.film = new Film();
    review.author = author;
    review.rating = 3;
    review.watchedOn = LocalDate.of(2026, 7, 17);
    review.metrics.put("story", (short) 3);
    when(reviews.findByIdAndFilmId(99L, 42L)).thenReturn(Optional.of(review));
    when(reviews.save(review)).thenReturn(review);

    FilmReviewDto result = new FilmApi(null, reviews, null, null, null, null, null).updateReview(
      42L,
      99L,
      new FilmReviewRequest((short) 5, "Mejor de lo que recordaba", LocalDate.of(2026, 7, 18), Map.of("story", (short) 4)),
      author
    );

    verify(reviews).findByIdAndFilmId(99L, 42L);
    assertEquals(5, result.rating());
    assertEquals(Map.of("story", (short) 4), result.metrics());
  }
}
