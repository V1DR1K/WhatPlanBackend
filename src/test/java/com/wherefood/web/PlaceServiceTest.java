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
import com.wherefood.application.PlaceInput;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Category;
import com.wherefood.domain.HighlightTag;
import com.wherefood.domain.Place;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.domain.Zone;
import com.wherefood.repo.Repositories.Categories;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.HighlightTags;
import com.wherefood.repo.Repositories.Places;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void create_requiresAnActiveZoneAndAssignsItToTheNewPlace() {
        User member = user(7L, Role.USER);
        Category category = new Category();
        category.id = 3L;
        category.active = true;
        ZoneSettingsService zoneSettings = mock(ZoneSettingsService.class);
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(places.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Zone zone = new Zone();
        zone.id = 2L;
        when(zoneSettings.requireActive(2L)).thenReturn(zone);
        PlaceService zonedService = new PlaceService(places, categories, tags,
                new CoupleAuthorizationService(members), zoneSettings);

        Place created = zonedService.create(new PlaceInput("Café", "Centro", null, null, false,
                3L, List.of(), 2L), member);

        assertEquals(2L, created.zoneId);
        verify(zoneSettings).requireActive(2L);
    }

    @Test
    void create_rejectsMissingZoneWhenAllZonesIsSelected() {
        User member = user(7L, Role.USER);
        Category category = new Category();
        category.id = 3L;
        category.active = true;
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        PlaceService zonedService = new PlaceService(places, categories, tags,
                new CoupleAuthorizationService(members), mock(ZoneSettingsService.class));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> zonedService.create(request(), member)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
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
    void update_whenChangingToInactiveCategory_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        Category current = new Category();
        current.id = 3L;
        Category inactive = new Category();
        inactive.id = 4L;
        inactive.active = false;
        Place existing = new Place();
        existing.id = 5L;
        existing.category = current;
        when(places.findDetailedByIdAndCoupleId(5L, coupleId)).thenReturn(Optional.of(existing));
        when(categories.findById(4L)).thenReturn(Optional.of(inactive));

        PlaceInput request = new PlaceInput("Café", "Centro", null, null, false, 4L, List.of());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(5L, request, member)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    @Test
    void create_whenAdminAttemptsPrivateContent_returns404WithoutSaving() {
        User admin = user(7L, Role.ADMIN);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(request(), admin)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    @Test
    void create_whenSelectingInactiveTag_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        HighlightTag tag = new HighlightTag();
        tag.id = 9L;
        tag.active = false;
        when(tags.findAllById(Set.of(9L))).thenReturn(List.of(tag));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(requestWithTags(List.of(9L)), member)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    @Test
    void update_whenKeepingPreviouslyAssignedInactiveTag_preservesIt() {
        User member = user(7L, Role.USER);
        Category category = new Category();
        category.id = 3L;
        Place existing = new Place();
        existing.id = 5L;
        existing.category = category;
        HighlightTag inactiveTag = new HighlightTag();
        inactiveTag.id = 9L;
        inactiveTag.active = false;
        existing.highlightTags.add(inactiveTag);
        when(places.findDetailedByIdAndCoupleId(5L, coupleId)).thenReturn(Optional.of(existing));
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(tags.findAllById(Set.of(9L))).thenReturn(List.of(inactiveTag));
        when(places.save(existing)).thenReturn(existing);

        Place updated = service.update(5L, requestWithTags(List.of(9L)), member);

        assertEquals(List.of(inactiveTag), List.copyOf(updated.highlightTags));
        verify(places).save(existing);
    }

    @Test
    void update_whenAssigningAnotherInactiveTag_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        Category category = new Category();
        category.id = 3L;
        Place existing = new Place();
        existing.id = 5L;
        existing.category = category;
        HighlightTag inactiveTag = new HighlightTag();
        inactiveTag.id = 9L;
        inactiveTag.active = false;
        when(places.findDetailedByIdAndCoupleId(5L, coupleId)).thenReturn(Optional.of(existing));
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(tags.findAllById(Set.of(9L))).thenReturn(List.of(inactiveTag));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(5L, requestWithTags(List.of(9L)), member)).getStatusCode().value());
        verify(places, never()).save(any(Place.class));
    }

    private static PlaceInput request() {
        return new PlaceInput("Café", "Centro", null, null, false, 3L, List.of());
    }

    private static PlaceInput requestWithTags(List<Long> tagIds) {
        return new PlaceInput("Café", "Centro", null, null, false, 3L, tagIds);
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
