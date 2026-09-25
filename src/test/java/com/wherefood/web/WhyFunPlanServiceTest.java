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

    private static User member() {
        User value = new User();
        value.id = 12L;
        value.role = Role.USER;
        return value;
    }
}
