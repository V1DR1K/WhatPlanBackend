package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunVisitReview;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.WhyFunVisits;
import com.wherefood.repo.Repositories.WhyFunVisitReviews;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

class WhyFunVisitReviewServiceTest {
    private final WhyFunVisitReviews reviews = mock(WhyFunVisitReviews.class);
    private final WhyFunVisits visits = mock(WhyFunVisits.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final WhyFunVisitReviewService service = new WhyFunVisitReviewService(
            reviews, visits, new CoupleAuthorizationService(members));
    private final UUID coupleId = UUID.randomUUID();

    @BeforeEach
    void establishMemberContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void update_whenActorOwnsReview_updatesWithinServiceCommand() {
        User owner = user(7L, Role.USER);
        WhyFunVisitReview review = review(owner);
        when(reviews.findDetailedByIdAndCoupleId(11L, coupleId)).thenReturn(Optional.of(review));
        when(reviews.save(review)).thenReturn(review);

        WhyFunVisitReview updated = service.update(11L, new ActivityReviewRequest((short) 4, "  Volvería  "), owner);

        assertEquals(4, updated.rating);
        assertEquals("  Volvería  ", updated.comment);
        assertEquals(owner, updated.updatedBy);
        verify(reviews).save(review);
    }

    @Test
    void update_whenActorDoesNotOwnReview_returns404WithoutSaving() {
        User owner = user(7L, Role.USER);
        User other = user(8L, Role.USER);
        when(reviews.findDetailedByIdAndCoupleId(11L, coupleId)).thenReturn(Optional.of(review(owner)));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(11L, new ActivityReviewRequest((short) 1, null), other))
                .getStatusCode().value());
        verify(reviews, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_whenAdminRequestsPrivateReview_returns404WithoutDeleting() {
        User owner = user(7L, Role.USER);
        User admin = user(9L, Role.ADMIN);
        when(reviews.findDetailedByIdAndCoupleId(11L, coupleId)).thenReturn(Optional.of(review(owner)));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.delete(11L, admin)).getStatusCode().value());
        verify(reviews, never()).delete(org.mockito.ArgumentMatchers.any(WhyFunVisitReview.class));
    }

    @Test
    void delete_whenAnotherCoupleMemberOwnsReview_returns404WithoutDeleting() {
        User owner = user(7L, Role.USER);
        User other = user(8L, Role.USER);
        when(reviews.findDetailedByIdAndCoupleId(11L, coupleId)).thenReturn(Optional.of(review(owner)));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.delete(11L, other)).getStatusCode().value());
        verify(reviews, never()).delete(org.mockito.ArgumentMatchers.any(WhyFunVisitReview.class));
    }

    @Test
    void reviewMutationTransactionLivesInServiceRatherThanController() throws Exception {
        assertTrue(WhyFunVisitReviewService.class.getMethod("update", Long.class,
                ActivityReviewRequest.class, User.class).isAnnotationPresent(Transactional.class));
        assertTrue(WhyFunVisitReviewService.class.getMethod("delete", Long.class, User.class)
                .isAnnotationPresent(Transactional.class));
        assertFalse(WhyFunActivityApi.class.getDeclaredMethod("updateReview", Long.class,
                ActivityReviewRequest.class, User.class).isAnnotationPresent(Transactional.class));
        assertFalse(WhyFunActivityApi.class.getDeclaredMethod("deleteReview", Long.class, User.class)
                .isAnnotationPresent(Transactional.class));
    }

    private static WhyFunVisitReview review(User owner) {
        WhyFunVisitReview review = new WhyFunVisitReview();
        review.id = 11L;
        review.author = owner;
        review.updatedBy = owner;
        return review;
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
