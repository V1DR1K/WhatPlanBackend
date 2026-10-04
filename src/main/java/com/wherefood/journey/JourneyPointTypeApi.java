package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/whither-journey/point-types")
@PreAuthorize("isAuthenticated()")
public class JourneyPointTypeApi {
    private final JourneyPointTypeService service;

    public JourneyPointTypeApi(JourneyPointTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<PointTypeDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PointTypeDto create(@Valid @RequestBody PointTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{code}")
    public PointTypeDto update(@PathVariable String code, @Valid @RequestBody PointTypeRequest request) {
        return service.update(code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        service.delete(code);
    }
}
