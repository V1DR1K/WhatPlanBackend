package com.wherefood.application;

import java.util.List;
import java.util.UUID;

/** Application input for creating or updating a shared place. */
public record PlaceInput(String name, String address, String sourceUrl, String mapsUrl,
        boolean acceptsReservations, Long categoryId, List<Long> tagIds, Long zoneId, UUID stageId) {
    public PlaceInput(String name, String address, String sourceUrl, String mapsUrl,
            boolean acceptsReservations, Long categoryId, List<Long> tagIds) {
        this(name, address, sourceUrl, mapsUrl, acceptsReservations, categoryId, tagIds, null, null);
    }

    public PlaceInput(String name, String address, String sourceUrl, String mapsUrl,
            boolean acceptsReservations, Long categoryId, List<Long> tagIds, Long zoneId) {
        this(name, address, sourceUrl, mapsUrl, acceptsReservations, categoryId, tagIds, zoneId, null);
    }
}
