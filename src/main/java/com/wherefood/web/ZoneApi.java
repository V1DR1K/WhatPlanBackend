package com.wherefood.web;

import com.wherefood.domain.User;
import com.wherefood.domain.Zone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

record ZoneRequest(@NotBlank @Size(max = 80) String name) {}
record ZoneDto(Long id, String name, boolean active, Instant createdAt, Instant updatedAt) {}
record ZonePreferenceRequest(Long zoneId) {}
record UserPreferenceDto(Long defaultZoneId) {}

@RestController
@RequestMapping("/api/zones")
public class ZoneApi {
    private final ZoneSettingsService service;

    public ZoneApi(ZoneSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<ZoneDto> activeZones() {
        return service.activeZones().stream().map(ZoneApi::dto).toList();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    List<ZoneDto> allZones() {
        return service.allZones().stream().map(ZoneApi::dto).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ZoneDto create(@RequestBody @Valid ZoneRequest request) {
        return dto(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ZoneDto update(@PathVariable Long id, @RequestBody @Valid ZoneRequest request) {
        return dto(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @GetMapping("/preference")
    @PreAuthorize("isAuthenticated()")
    UserPreferenceDto preference(@AuthenticationPrincipal User user) {
        return service.preference(user);
    }

    @PutMapping("/preference")
    @PreAuthorize("isAuthenticated()")
    UserPreferenceDto updatePreference(@AuthenticationPrincipal User user,
            @RequestBody @Valid ZonePreferenceRequest request) {
        return service.updatePreference(user, request);
    }

    private static ZoneDto dto(Zone zone) {
        return new ZoneDto(zone.id, zone.name, zone.active, zone.createdAt, zone.updatedAt);
    }
}
