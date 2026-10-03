package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class LocationApi {
    private final LocationService service;

    public LocationApi(LocationService service) {
        this.service = service;
    }

    @GetMapping("/cities/countries")
    public List<CountryDto> countries() {
        return service.countries();
    }

    @GetMapping("/cities")
    public List<CityDto> cities(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String search) {
        return service.cities(countryCode, search);
    }

    @GetMapping("/cities/{id}")
    public CityDto city(@PathVariable Long id) {
        return service.city(id);
    }

    @PostMapping("/cities")
    public CityDto city(@Valid @RequestBody CityRequest request) {
        return service.createCity(request);
    }

    @GetMapping("/location-context")
    public LocationContext context() {
        return service.context();
    }

    @PutMapping("/location-context/origin")
    public LocationContext origin(@Valid @RequestBody OriginRequest request) {
        return service.saveOrigin(request.cityId());
    }
}
