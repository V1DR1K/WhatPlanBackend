package com.wherefood.repo;

import com.wherefood.domain.JourneyPointType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JourneyPointTypes extends JpaRepository<JourneyPointType, UUID> {
    List<JourneyPointType> findByCoupleIdOrderByPositionAscCodeAsc(UUID coupleId);

    Optional<JourneyPointType> findByCoupleIdAndCode(UUID coupleId, String code);

    boolean existsByCoupleIdAndNameIgnoreCaseAndCodeNot(UUID coupleId, String name, String code);

    boolean existsByCoupleIdAndNameIgnoreCase(UUID coupleId, String name);
}
