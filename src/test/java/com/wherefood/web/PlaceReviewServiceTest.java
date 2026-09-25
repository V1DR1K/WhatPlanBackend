package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceReview;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.PlaceReviews;
import com.wherefood.repo.Repositories.Places;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlaceReviewServiceTest {
    private final Places places = mock(Places.class);
    private final PlaceReviews reviews = mock(PlaceReviews.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final PlaceReviewService service = new PlaceReviewService(places, reviews,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishCouple() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(7L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearContext() {
        CoupleContext.clear();
    }

    @Test
    void saveOwn_whenMemberRatesActivePlace_savesReviewForThatMember() {
        User actor = user(7L, Role.USER);
        Place place = new Place();
        place.id = 4L;
        when(places.findByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place));
        when(reviews.findByPlaceIdAndAuthorId(4L, actor.id)).thenReturn(Optional.empty());
        when(reviews.save(any(PlaceReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlaceReview saved = service.saveOwn(4L,
                new PlaceReviewRequest("Good", (short) 4, null, null, null, null, null, null), actor);

        assertEquals(actor, saved.author);
        assertEquals(place, saved.place);
        assertEquals(Short.valueOf((short) 4), saved.location);
        verify(reviews).save(saved);
    }

    @Test
    void saveOwn_whenAdminAttemptsPrivateReview_returns404WithoutSaving() {
        User admin = user(7L, Role.ADMIN);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.saveOwn(4L,
                        new PlaceReviewRequest(null, (short) 4, null, null, null, null, null, null), admin))
                .getStatusCode().value());
        verify(reviews, never()).save(any(PlaceReview.class));
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
