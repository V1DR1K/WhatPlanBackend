package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunCategory;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.domain.WhyFunVenueReview;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.WhyFunCategories;
import com.wherefood.repo.Repositories.WhyFunVenueReviews;
import com.wherefood.repo.Repositories.WhyFunVenues;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WhyFunPlanServiceTest {
    private final WhyFunCategories categories = mock(WhyFunCategories.class);
    private final WhyFunVenues venues = mock(WhyFunVenues.class);
    private final WhyFunVenueReviews reviews = mock(WhyFunVenueReviews.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final WhyFunPlanService service = new WhyFunPlanService(categories, venues, reviews,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishMembership() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(12L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearContext() {
        CoupleContext.clear();
    }

    @Test
    void update_whenPlanIsOutsideCurrentCouple_returns404WithoutSaving() {
        when(venues.findDetailedByIdAndCoupleId(54L, coupleId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(54L, null, member()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(venues, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveOwnReview_whenSecondMemberRatesPlan_savesOnlyUnderThatAuthor() {
        User secondMember = member();
        secondMember.id = 13L;
        when(members.findActiveCoupleIdByUserId(secondMember.id)).thenReturn(Optional.of(coupleId));
        WhyFunVenue plan = new WhyFunVenue();
        plan.id = 66L;
        when(venues.findDetailedByIdAndCoupleId(plan.id, coupleId)).thenReturn(Optional.of(plan));
        when(reviews.findByVenueIdAndAuthorIdAndCoupleId(plan.id, secondMember.id, coupleId))
                .thenReturn(Optional.empty());
        when(reviews.save(org.mockito.ArgumentMatchers.any(WhyFunVenueReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WhyFunVenueReview saved = service.saveOwnReview(plan.id, new FunReviewRequest((short) 4, "Buena"), secondMember);

        assertEquals(secondMember, saved.author);
        assertEquals(plan, saved.venue);
        verify(reviews).findByVenueIdAndAuthorIdAndCoupleId(plan.id, secondMember.id, coupleId);
        verify(reviews).save(saved);
    }

    @Test
    void update_whenKeepingPreviouslyAssignedInactiveCategories_preservesThem() {
        User actor = member();
        WhyFunCategory category = new WhyFunCategory();
        category.id = 3L;
        category.active = false;
        WhyFunCategory subcategory = new WhyFunCategory();
        subcategory.id = 4L;
        subcategory.parent = category;
        subcategory.active = false;
        WhyFunVenue plan = new WhyFunVenue();
        plan.id = 66L;
        plan.category = category;
        plan.subcategory = subcategory;
        when(venues.findDetailedByIdAndCoupleId(plan.id, coupleId)).thenReturn(Optional.of(plan));
        when(categories.findDetailedById(category.id)).thenReturn(Optional.of(category));
        when(categories.findDetailedById(subcategory.id)).thenReturn(Optional.of(subcategory));
        when(venues.save(plan)).thenReturn(plan);
        FunPlanRequest request = new FunPlanRequest("Plan editado", "Dirección", null, category.id,
                subcategory.id, null);

        WhyFunVenue updated = service.update(plan.id, request.toInput(), actor);

        assertEquals(category, updated.category);
        assertEquals(subcategory, updated.subcategory);
        verify(venues).save(plan);
    }

    private static User member() {
        User value = new User();
        value.id = 12L;
        value.role = Role.USER;
        return value;
    }
}
