package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Category;
import com.wherefood.domain.Place;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Categories;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.HighlightTags;
import com.wherefood.repo.Repositories.Places;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlaceServiceTest {
    private final Places places = mock(Places.class);
    private final Categories categories = mock(Categories.class);
    private final HighlightTags tags = mock(HighlightTags.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final PlaceService service = new PlaceService(places, categories, tags,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishMember() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(7L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearContext() {
        CoupleContext.clear();
    }

    @Test
    void create_whenMemberSelectsActiveCategory_savesPendingPlace() {
        User member = user(7L, Role.USER);
        Category category = new Category();
        category.id = 3L;
        category.active = true;
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(places.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Place created = service.create(request(), member);

        assertEquals("Café", created.name);
        assertEquals(category, created.category);
        assertNotNull(created.createdAt);
        verify(places).save(created);
    }

    @Test
    void update_whenIdBelongsToAnotherCouple_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        when(places.findDetailedByIdAndCoupleId(5L, coupleId)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(5L, request(), member)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    @Test
    void create_whenAdminAttemptsPrivateContent_returns404WithoutSaving() {
        User admin = user(7L, Role.ADMIN);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(request(), admin)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    private static PlaceRequest request() {
        return new PlaceRequest("Café", "Centro", null, null, false, 3L, List.of());
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
