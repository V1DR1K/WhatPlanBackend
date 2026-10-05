package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import com.wherefood.config.CoupleContext;
import com.wherefood.domain.*;
import com.wherefood.repo.JourneyRepositories.*;
import com.wherefood.repo.Repositories.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LocationService {
    @org.springframework.beans.factory.annotation.Value("${app.journey.max-upload-bytes:10485760}")
    private long maxUploadBytes = 10485760;

    private final Zones zones;
    private final Couples couples;
    private final Journeys journeys;
    private final Stages stages;

    public LocationService(Zones zones, Couples couples, Journeys journeys, Stages stages) {
        this.zones = zones;
        this.couples = couples;
        this.journeys = journeys;
        this.stages = stages;
    }

    public static UUID couple() {
        UUID id = CoupleContext.current();
        if (id == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Necesitás una pareja activa");
        return id;
    }

    public CityDto city(Long id) {
        Zone z =
                zones.findById(id)
                        .filter(v -> v.active)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Elegí una ciudad disponible"));
        return new CityDto(z.id, z.name, z.countryCode);
    }

    public List<CountryDto> countries() {
        return Arrays.stream(Locale.getISOCountries())
                .map(
                        code ->
                                new CountryDto(
                                        code,
                                        Locale.forLanguageTag("und-" + code)
                                                .getDisplayCountry(Locale.forLanguageTag("es-AR"))))
                .sorted(Comparator.comparing(CountryDto::name))
                .toList();
    }

    public List<CityDto> cities(String country, String search) {
        return zones.findByActiveTrueOrderByNameAsc().stream()
                .filter(z -> country == null || z.countryCode.equalsIgnoreCase(country))
                .filter(
                        z ->
                                search == null
                                        || z.name.toLowerCase(Locale.ROOT)
                                                .contains(search.toLowerCase(Locale.ROOT)))
                .limit(100)
                .map(z -> new CityDto(z.id, z.name, z.countryCode))
                .toList();
    }

    public List<CityDto> citiesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return zones.findAllById(ids).stream()
                .filter(zone -> zone.active)
                .sorted(Comparator.comparing((Zone zone) -> zone.name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(zone -> zone.countryCode))
                .map(zone -> new CityDto(zone.id, zone.name, zone.countryCode))
                .toList();
    }

    @Transactional
    public CityDto createCity(CityRequest request) {
        String country = request.countryCode().toUpperCase(Locale.ROOT);
        if (!Arrays.asList(Locale.getISOCountries()).contains(country))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "País inválido");
        String name = request.name().trim().replaceAll("\\s+", " ");
        if (name.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá una ciudad");
        Zone existing = zones.findByCountryCodeAndNameIgnoreCase(country, name).orElse(null);
        if (existing != null) {
            if (!existing.active)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "La ciudad está dada de baja");
            return new CityDto(existing.id, existing.name, existing.countryCode);
        }
        Zone z = new Zone();
        z.name = name;
        z.countryCode = country;
        z.createdAt = z.updatedAt = Instant.now();
        zones.saveAndFlush(z);
        return new CityDto(z.id, z.name, z.countryCode);
    }

    public Long origin() {
        return couples.findById(couple())
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Pareja no encontrada"))
                .originCityId;
    }

    @Transactional
    public LocationContext saveOrigin(Long cityId) {
        city(cityId);
        Couple c = couples.findById(couple()).orElseThrow();
        c.originCityId = cityId;
        couples.save(c);
        return context();
    }

    public LocationContext context() {
        Long origin = origin();
        CityDto home = city(origin);
        List<LocationOption> options = new ArrayList<>();
        options.add(new LocationOption("origin", origin, null, null, home.name() + " · Origen"));
        var activeTrips =
                journeys.findByCoupleIdAndArchivedFalseOrderByStartsOnDescIdDesc(couple());
        var stageGroups =
                activeTrips.isEmpty()
                        ? Map.<UUID, List<JourneyStage>>of()
                        : stages
                                .findByCoupleIdAndJourneyIdIn(
                                        couple(), activeTrips.stream().map(t -> t.id).toList())
                                .stream()
                                .collect(java.util.stream.Collectors.groupingBy(s -> s.journeyId));
        for (Journey trip : activeTrips) {
            for (JourneyStage stage :
                    stageGroups.getOrDefault(trip.id, List.of()).stream()
                            .sorted(Comparator.comparingInt(s -> s.position))
                            .toList()) {
                CityDto c = city(stage.cityId);
                String label =
                        c.name()
                                + " · "
                                + trip.name
                                + " · "
                                + stage.startsOn
                                + " / "
                                + stage.endsOn;
                options.add(
                        new LocationOption(
                                stage.id.toString(), stage.cityId, stage.id, trip.id, label));
            }
        }
        Map<String, Long> duplicates =
                options.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        LocationOption::label,
                                        java.util.stream.Collectors.counting()));
        List<LocationOption> identified =
                options.stream()
                        .map(
                                o ->
                                        duplicates.get(o.label()) > 1
                                                ? new LocationOption(
                                                        o.key(),
                                                        o.cityId(),
                                                        o.stageId(),
                                                        o.journeyId(),
                                                        o.label() + " · " + o.key().substring(0, 8))
                                                : o)
                        .toList();
        return new LocationContext(couple(), origin, identified, maxUploadBytes);
    }
}
