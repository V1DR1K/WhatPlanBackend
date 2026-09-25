package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.FilmReview;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.FilmReviews;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FilmReviewServiceTest {
    private final FilmReviews reviews = mock(FilmReviews.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final FilmReviewService service = new FilmReviewService(reviews,
            new CoupleAuthorizationService(members), null);

    @BeforeEach
    void establishCoupleContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(8L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void update_whenAnotherMemberOwnsReview_returns404WithoutSaving() {
        User author = user(7L);
        User otherMember = user(8L);
        FilmReview review = review(author);
        when(reviews.findByIdAndFilmIdAndCoupleId(21L, 14L, coupleId)).thenReturn(Optional.of(review));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(14L, 21L,
                        new FilmReviewRequest((short) 5, "Edit", null, null, Map.of()), otherMember))
                .getStatusCode().value());
        verify(reviews, never()).save(review);
    }

    @Test
    void delete_whenAnotherMemberOwnsReview_returns404WithoutDeleting() {
        User author = user(7L);
        User otherMember = user(8L);
        FilmReview review = review(author);
        when(reviews.findByIdAndFilmIdAndCoupleId(21L, 14L, coupleId)).thenReturn(Optional.of(review));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.delete(14L, 21L, otherMember)).getStatusCode().value());
        verify(reviews, never()).delete(review);
    }

    private static FilmReview review(User author) {
        FilmReview review = new FilmReview();
        review.id = 21L;
        review.author = review.updatedBy = author;
        review.rating = 2;
        return review;
    }

    private static User user(Long id) {
        User user = new User();
        user.id = id;
        user.role = Role.USER;
        return user;
    }
}
