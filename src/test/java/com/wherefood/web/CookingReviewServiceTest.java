package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.CookingReview;
import com.wherefood.domain.Cooking;
import com.wherefood.repo.Repositories.Cookings;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.CookingReviews;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CookingReviewServiceTest {
    private final CookingReviews reviews = mock(CookingReviews.class);
    private final Cookings cookings = mock(Cookings.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final CookingReviewService service = new CookingReviewService(reviews, cookings,
            new CoupleAuthorizationService(members));
    private final UUID coupleId = UUID.randomUUID();

    @BeforeEach
    void establishActiveCouple() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void create_whenMemberHasNotReviewed_savesReviewForScopedCooking() {
        User owner = user(4L, Role.USER);
        Cooking cooking = new Cooking();
        cooking.id = 3L;
        when(cookings.findDetailedByIdAndCoupleId(3L, coupleId)).thenReturn(Optional.of(cooking));
        when(reviews.findByCookingIdAndAuthorIdAndCoupleId(3L, owner.id, coupleId)).thenReturn(Optional.empty());
        when(reviews.save(org.mockito.ArgumentMatchers.any(CookingReview.class)))
                .thenAnswer(call -> call.getArgument(0));

        CookingReview saved = service.create(3L,
                new CookingReviewRequest((short) 4, (short) 3, (short) 5, "Buena"), owner);

        assertEquals(cooking, saved.cooking);
        assertEquals(owner, saved.author);
        assertEquals(4, saved.rating);
        verify(reviews).save(saved);
    }

    @Test
    void update_whenActorOwnsReview_savesChangedValues() {
        User owner = user(4L, Role.USER);
        CookingReview review = review(owner);
        when(reviews.findDetailedByIdAndCoupleId(9L, coupleId)).thenReturn(Optional.of(review));
        when(reviews.save(review)).thenReturn(review);

        CookingReview saved = service.update(9L,
                new CookingReviewRequest((short) 5, (short) 3, (short) 4, "  Rica  "), owner);

        assertEquals(5, saved.rating);
        assertEquals(3, saved.complexity);
        assertEquals(4, saved.taste);
        assertEquals("  Rica  ", saved.comment);
        verify(reviews).save(review);
    }

    @Test
    void update_whenActorDoesNotOwnReview_returns404WithoutChangingIt() {
        User owner = user(4L, Role.USER);
        User other = user(5L, Role.USER);
        CookingReview review = review(owner);
        when(reviews.findDetailedByIdAndCoupleId(9L, coupleId)).thenReturn(Optional.of(review));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(9L, new CookingReviewRequest((short) 1, (short) 1, (short) 1, null), other))
                .getStatusCode().value());
        verify(reviews, never()).save(org.mockito.ArgumentMatchers.any(CookingReview.class));
    }

    private static CookingReview review(User owner) {
        CookingReview review = new CookingReview();
        review.id = 9L;
        review.author = review.updatedBy = owner;
        return review;
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
