package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunCategory;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.WhyFunCategories;
import com.wherefood.repo.Repositories.WhyFunVenues;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WhyFunActivityServiceTest {
    private final WhyFunCategories categories = mock(WhyFunCategories.class);
    private final WhyFunVenues activities = mock(WhyFunVenues.class);
    private final CoupleMembers members = mock(CoupleMembers.class);

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void update_whenKeepingPreviouslyAssignedInactiveCategories_preservesThem() {
        UUID coupleId = UUID.randomUUID();
        CoupleContext.set(coupleId);
        User actor = new User();
        actor.id = 7L;
        actor.role = Role.USER;
        when(members.findActiveCoupleIdByUserId(actor.id)).thenReturn(Optional.of(coupleId));
        WhyFunCategory category = new WhyFunCategory();
        category.id = 3L;
        category.active = false;
        WhyFunCategory subcategory = new WhyFunCategory();
        subcategory.id = 4L;
        subcategory.parent = category;
        subcategory.active = false;
        WhyFunVenue activity = new WhyFunVenue();
        activity.id = 55L;
        activity.category = category;
        activity.subcategory = subcategory;
        when(activities.findDetailedByIdAndCoupleId(activity.id, coupleId)).thenReturn(Optional.of(activity));
        when(categories.findDetailedById(category.id)).thenReturn(Optional.of(category));
        when(categories.findDetailedById(subcategory.id)).thenReturn(Optional.of(subcategory));
        when(activities.save(activity)).thenReturn(activity);

        WhyFunVenue updated = new WhyFunActivityService(categories, activities,
                new CoupleAuthorizationService(members)).update(activity.id,
                        new ActivityRequest("Actividad editada", "Dirección", category.id, subcategory.id, false, null, null, null),
                        actor);

        assertEquals(category, updated.category);
        assertEquals(subcategory, updated.subcategory);
    }
}
