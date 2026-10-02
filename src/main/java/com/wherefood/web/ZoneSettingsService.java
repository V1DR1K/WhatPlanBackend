package com.wherefood.web;

import com.wherefood.domain.User;
import com.wherefood.domain.Zone;
import com.wherefood.repo.Repositories.Users;
import com.wherefood.repo.Repositories.Zones;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ZoneSettingsService {
    private final Zones zones;
    private final Users users;

    public ZoneSettingsService(Zones zones, Users users) {
        this.zones = zones;
        this.users = users;
    }

    public List<Zone> activeZones() {
        return zones.findByActiveTrueOrderByNameAsc();
    }

    public List<Zone> allZones() {
        return zones.findAllByOrderByNameAsc();
    }

    @Transactional
    public Zone create(ZoneRequest request) {
        String name = normalize(request.name());
        if (zones.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una Zona con ese nombre");
        }
        Zone zone = new Zone();
        zone.name = name;
        zone.active = true;
        zone.createdAt = zone.updatedAt = Instant.now();
        return zones.save(zone);
    }

    @Transactional
    public Zone update(Long id, ZoneRequest request) {
        Zone zone = zones.findById(id).orElseThrow(() -> notFound());
        String name = normalize(request.name());
        zones.findByNameIgnoreCase(name).filter(other -> !other.id.equals(id)).ifPresent(other -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una Zona con ese nombre");
        });
        zone.name = name;
        zone.updatedAt = Instant.now();
        return zones.save(zone);
    }

    @Transactional
    public void deactivate(Long id) {
        Zone zone = zones.findById(id).orElseThrow(() -> notFound());
        zone.active = false;
        zone.updatedAt = Instant.now();
        zones.save(zone);
        users.clearDefaultZone(id);
    }

    @Transactional
    public UserPreferenceDto preference(User principal) {
        User user = currentUser(principal);
        return new UserPreferenceDto(user.defaultZoneId);
    }

    @Transactional
    public UserPreferenceDto updatePreference(User principal, ZonePreferenceRequest request) {
        User user = currentUser(principal);
        if (request.zoneId() != null) activeZone(request.zoneId());
        user.defaultZoneId = request.zoneId();
        users.save(user);
        return new UserPreferenceDto(user.defaultZoneId);
    }

    public Zone requireActive(Long id) {
        return activeZone(id);
    }

    private User currentUser(User principal) {
        return users.findById(principal.id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión de usuario ya no está disponible"));
    }

    private Zone activeZone(Long id) {
        return zones.findById(id).filter(zone -> zone.active).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una Zona activa"));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada");
    }
}
