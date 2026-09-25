package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.PlaceVisitReview;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.PlaceVisitReviews;
import com.wherefood.repo.Repositories.PlaceVisits;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlaceVisitReviewServiceTest {
    private final PlaceVisitReviews reviews = mock(PlaceVisitReviews.class);
    private final PlaceVisits visits = mock(PlaceVisits.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final PlaceVisitReviewService service = new PlaceVisitReviewService(reviews, visits,
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
    void update_whenActorOwnsReview_savesUpdatedReview() {
        User owner = user(4L, Role.USER);
        PlaceVisitReview review = review(owner, false);
        when(reviews.findDetailedByIdAndCoupleId(13L, coupleId)).thenReturn(Optional.of(review));
        when(reviews.save(review)).thenReturn(review);

        PlaceVisitReview saved = service.update(13L,
                new PlaceVisitReviewRequest((short) 5, "  Excelente  ", (short) 4, (short) 3), owner);

        assertEquals(Short.valueOf((short) 5), saved.overall);
        assertEquals("  Excelente  ", saved.comment);
        assertEquals(Short.valueOf((short) 4), saved.taste);
        assertEquals(Short.valueOf((short) 3), saved.price);
        assertEquals(owner, saved.updatedBy);
        verify(reviews).save(review);
    }

    @Test
    void create_whenMemberOwnsActiveCouple_savesPersonalReview() {
        User owner = user(4L, Role.USER);
        Place place = new Place();
        PlaceVisit visit = new PlaceVisit();
        visit.id = 21L;
        visit.place = place;
        when(visits.findDetailedByIdAndCoupleId(21L, coupleId)).thenReturn(Optional.of(visit));
        when(reviews.findByVisitIdAndAuthorId(21L, owner.id)).thenReturn(Optional.empty());
        when(reviews.save(org.mockito.ArgumentMatchers.any(PlaceVisitReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlaceVisitReview created = service.create(21L,
                new PlaceVisitReviewRequest((short) 5, "Muy bueno", (short) 4, null), owner);

        assertEquals(visit, created.visit);
        assertEquals(owner, created.author);
        assertEquals(owner, created.updatedBy);
        assertEquals("Muy bueno", created.comment);
        verify(reviews).save(created);
    }

    @Test
    void saveOwn_whenNoPreviousReview_createsReviewForAuthenticatedAuthor() {
        User owner = user(4L, Role.USER);
        PlaceVisit visit = new PlaceVisit();
        visit.id = 21L;
        visit.place = new Place();
        when(visits.findDetailedByIdAndCoupleId(21L, coupleId)).thenReturn(Optional.of(visit));
        when(reviews.findByVisitIdAndAuthorId(21L, owner.id)).thenReturn(Optional.empty());
        when(reviews.save(org.mockito.ArgumentMatchers.any(PlaceVisitReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlaceVisitReview saved = service.saveOwn(21L,
                new PlaceVisitReviewRequest((short) 3, null, null, (short) 2), owner);

        assertEquals(owner, saved.author);
        assertEquals(owner, saved.updatedBy);
        assertEquals(Short.valueOf((short) 3), saved.overall);
        assertEquals(Short.valueOf((short) 2), saved.price);
        verify(reviews).save(saved);
    }

    @Test
    void update_whenPlaceIsArchived_hidesReviewAndDoesNotSave() {
        User owner = user(4L, Role.USER);
        when(reviews.findDetailedByIdAndCoupleId(13L, coupleId)).thenReturn(Optional.of(review(owner, true)));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(13L, new PlaceVisitReviewRequest((short) 5, null, null, null), owner))
                .getStatusCode().value());
        verify(reviews, never()).save(org.mockito.ArgumentMatchers.any(PlaceVisitReview.class));
    }

    @Test
    void delete_whenAnotherMemberOwnsReview_returns404WithoutDeleting() {
        User owner = user(4L, Role.USER);
        User other = user(5L, Role.USER);
        when(reviews.findDetailedByIdAndCoupleId(13L, coupleId)).thenReturn(Optional.of(review(owner, false)));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.delete(13L, other)).getStatusCode().value());
        verify(reviews, never()).delete(org.mockito.ArgumentMatchers.any(PlaceVisitReview.class));
    }

    private static PlaceVisitReview review(User owner, boolean archived) {
        Place place = new Place();
        if (archived) place.deactivatedAt = java.time.Instant.now();
        PlaceVisit visit = new PlaceVisit();
        visit.place = place;
        PlaceVisitReview review = new PlaceVisitReview();
        review.id = 13L;
        review.visit = visit;
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
