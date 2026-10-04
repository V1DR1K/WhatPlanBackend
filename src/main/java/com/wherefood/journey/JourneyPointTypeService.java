package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import com.wherefood.config.CoupleContext;
import com.wherefood.domain.JourneyPointType;
import com.wherefood.repo.JourneyPointTypes;
import com.wherefood.repo.JourneyRepositories.Points;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JourneyPointTypeService {
    private record DefaultType(String code, String name, String icon, String color, int position) {}

    private static final List<DefaultType> DEFAULTS = List.of(
            new DefaultType("GENERAL", "Actividad", "ACTIVITY", "#B9DCE9", 0),
            new DefaultType("FOOD", "WhereFood", "FOOD", "#FF8A00", 1),
            new DefaultType("FILM", "WhichMovie", "FILM", "#B8ADFF", 2),
            new DefaultType("COOK", "WhoCook", "COOK", "#D4EF55", 3),
            new DefaultType("FUN", "WhyFun", "FUN", "#FFD166", 4),
            new DefaultType("TRANSFER", "Traslado", "TRANSFER", "#83D8F5", 5));

    private final JourneyPointTypes types;
    private final Points points;

    public JourneyPointTypeService(JourneyPointTypes types, Points points) {
        this.types = types;
        this.points = points;
    }

    @Transactional
    public List<PointTypeDto> list() {
        ensureDefaults();
        return types.findByCoupleIdOrderByPositionAscCodeAsc(couple()).stream()
                .map(JourneyPointTypeService::dto)
                .toList();
    }

    @Transactional
    public PointTypeDto create(PointTypeRequest request) {
        ensureDefaults();
        ensureUniqueName(request.name(), null);
        JourneyPointType value = new JourneyPointType();
        value.code = "CUSTOM_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        value.name = request.name().trim();
        value.icon = request.icon();
        value.color = request.color().toUpperCase();
        value.position = types.findByCoupleIdOrderByPositionAscCodeAsc(couple()).stream()
                .mapToInt(type -> type.position).max().orElse(-1) + 1;
        value.builtIn = false;
        types.saveAndFlush(value);
        return find(value.code);
    }

    @Transactional
    public PointTypeDto update(String code, PointTypeRequest request) {
        ensureDefaults();
        JourneyPointType value = types.findByCoupleIdAndCode(couple(), code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de punto no encontrado"));
        ensureUniqueName(request.name(), code);
        value.name = request.name().trim();
        value.icon = request.icon();
        value.color = request.color().toUpperCase();
        types.saveAndFlush(value);
        return find(code);
    }

    @Transactional
    public void delete(String code) {
        ensureDefaults();
        JourneyPointType value = types.findByCoupleIdAndCode(couple(), code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de punto no encontrado"));
        if (value.builtIn) throw new ResponseStatusException(HttpStatus.CONFLICT, "Los tipos originales se pueden editar, pero no borrar");
        if (points.existsByCoupleIdAndCategory(couple(), code))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este tipo está usado en el recorrido de un viaje");
        types.delete(value);
    }

    @Transactional
    public String requireCategory(String code) {
        ensureDefaults();
        if (code == null || code.isBlank() || types.findByCoupleIdAndCode(couple(), code).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí un tipo de punto disponible");
        return code;
    }

    private void ensureDefaults() {
        UUID couple = couple();
        for (DefaultType initial : DEFAULTS) {
            if (types.findByCoupleIdAndCode(couple, initial.code()).isPresent()) continue;
            JourneyPointType value = new JourneyPointType();
            value.code = initial.code();
            value.name = initial.name();
            value.icon = initial.icon();
            value.color = initial.color();
            value.position = initial.position();
            value.builtIn = true;
            types.saveAndFlush(value);
        }
    }

    private void ensureUniqueName(String name, String exceptCode) {
        boolean duplicate = exceptCode == null
                ? types.existsByCoupleIdAndNameIgnoreCase(couple(), name.trim())
                : types.existsByCoupleIdAndNameIgnoreCaseAndCodeNot(couple(), name.trim(), exceptCode);
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un tipo con ese nombre");
    }

    private PointTypeDto find(String code) {
        return types.findByCoupleIdAndCode(couple(), code).map(JourneyPointTypeService::dto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de punto no encontrado"));
    }

    private static PointTypeDto dto(JourneyPointType value) {
        return new PointTypeDto(value.code, value.name, value.icon, value.color, value.position, value.builtIn);
    }

    private UUID couple() {
        UUID current = CoupleContext.current();
        if (current == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay pareja activa");
        return current;
    }
}
