package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.User;
import com.wherefood.domain.Zone;
import com.wherefood.repo.Repositories.Users;
import com.wherefood.repo.Repositories.Zones;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

class ZoneSettingsServiceTest {
    private final Zones zones = mock(Zones.class);
    private final Users users = mock(Users.class);
    private final ZoneSettingsService service = new ZoneSettingsService(zones, users);

    @Test
    void persistsZoneAsUserDefaultAndAllowsAllZonesPreference() {
        User user = user(8L);
        Zone rosario = zone(1L, true);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        when(zones.findById(1L)).thenReturn(Optional.of(rosario));
        when(users.save(user)).thenReturn(user);

        assertEquals(1L, service.updatePreference(user, new ZonePreferenceRequest(1L)).defaultZoneId());
        assertEquals(1L, user.defaultZoneId);
        assertNull(service.updatePreference(user, new ZonePreferenceRequest(null)).defaultZoneId());
        assertNull(user.defaultZoneId);
        verify(users, times(2)).save(user);
    }

    @Test
    void rejectsInactiveZoneAsDefault() {
        User user = user(8L);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        when(zones.findById(2L)).thenReturn(Optional.of(zone(2L, false)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updatePreference(user, new ZonePreferenceRequest(2L)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void deactivatesZoneWithoutDeletingItAndClearsUserDefaults() {
        Zone zone = zone(2L, true);
        when(zones.findById(2L)).thenReturn(Optional.of(zone));
        when(zones.save(zone)).thenReturn(zone);

        service.deactivate(2L);

        assertEquals(false, zone.active);
        verify(users).clearDefaultZone(2L);
        verify(zones).save(zone);
    }

    @Test
    void normalizesNamesAndRejectsDuplicates() {
        when(zones.findByNameIgnoreCase("Córdoba Capital")).thenReturn(Optional.empty());
        when(zones.save(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Zone created = service.create(new ZoneRequest("  Córdoba   Capital "));

        assertEquals("Córdoba Capital", created.name);
        when(zones.findByNameIgnoreCase("Rosario")).thenReturn(Optional.of(zone(1L, true)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.create(new ZoneRequest("Rosario"))).getStatusCode());
    }

    @Test
    void separatesUserPreferenceFromAdminOnlyZoneManagement() throws Exception {
        assertEquals("isAuthenticated()", ZoneApi.class.getDeclaredMethod("activeZones")
                .getAnnotation(PreAuthorize.class).value());
        assertEquals("isAuthenticated()", ZoneApi.class.getDeclaredMethod("preference", User.class)
                .getAnnotation(PreAuthorize.class).value());
        for (String method : new String[] {"allZones", "create", "update", "deactivate"}) {
            boolean adminOnly = java.util.Arrays.stream(ZoneApi.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method))
                    .map(candidate -> candidate.getAnnotation(PreAuthorize.class))
                    .anyMatch(access -> access != null && "hasRole('ADMIN')".equals(access.value()));
            assertEquals(true, adminOnly, method + " must be admin only");
        }
    }

    private static User user(Long id) {
        User user = new User();
        user.id = id;
        return user;
    }

    private static Zone zone(Long id, boolean active) {
        Zone zone = new Zone();
        zone.id = id;
        zone.active = active;
        zone.name = id == 1L ? "Rosario" : "Buenos Aires";
        return zone;
    }
}
