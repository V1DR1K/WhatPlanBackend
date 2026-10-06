package com.wherefood.journey;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wherefood.validation.SafeHttpUrl;

import jakarta.validation.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class JourneyDtos {
    private JourneyDtos() {}

    public record CountryDto(String code, String name) {}

    public record CityDto(Long id, String name, String countryCode) {}

    public record CityRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(min = 2, max = 2) String countryCode) {}

    public record OriginRequest(@NotNull @Positive Long cityId) {}

    public record LocationOption(
            String key, Long cityId, UUID stageId, UUID journeyId, String label) {}

    public record LocationContext(
            UUID coupleId, Long originCityId, List<LocationOption> options, long maxUploadBytes) {}

    public record StageRequest(
            UUID id,
            @NotNull @Positive Long cityId,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {}

    public record TripRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Size(min = 1, max = 1) List<@Valid StageRequest> stages,
            @Min(1) @Max(100) Integer maxTripPhotos,
            @Min(1) @Max(100) Integer maxDayPhotos) {
        public TripRequest(String name, LocalDate startsOn, LocalDate endsOn,
                List<StageRequest> stages) {
            this(name, startsOn, endsOn, stages, 20, 10);
        }
    }

    public record StageDto(
            UUID id,
            Long cityId,
            String cityName,
            String countryCode,
            LocalDate startsOn,
            LocalDate endsOn,
            int position) {}

    public record TripDto(
            UUID id,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean archived,
            List<StageDto> stages,
            UUID coverPhotoId,
            String coverPhotoUrl,
            int maxTripPhotos,
            int maxDayPhotos) {}

    public record SourceRef(
            @NotBlank @Pattern(regexp = "FOOD|FILM|COOK|FUN") String section,
            @NotNull @Positive Long entityId,
            @Positive Long experienceId) {}

    public record PointActionRequest(
            @NotBlank @Size(max = 40) String label,
            @NotBlank @Pattern(regexp = "ACTIVITY|FOOD|FILM|COOK|FUN|TRANSFER|SHOP|TICKET|PHONE|LINK|INFO|WEB") String icon,
            @NotBlank @Size(max = 1000) @SafeHttpUrl String url) {}

    public record PointActionDto(String label, String icon, String url) {}

    public record PointTypeRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "ACTIVITY|FOOD|FILM|COOK|FUN|TRANSFER|SHOP|TICKET|PHONE|LINK|INFO|WEB") String icon,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color) {}

    public record PointTypeDto(String code, String name, String icon, String color, int position, boolean builtIn) {}

    public record PointRequest(
            @NotNull UUID stageId,
            @NotBlank @Size(max = 160) String title,
            LocalDate scheduledOn,
            LocalTime scheduledTime,
            @Size(max = 4000) String notes,
            @Size(max = 1000) @SafeHttpUrl String mapsUrl,
            @Min(0) int position,
            @NotBlank @Pattern(regexp = "PENDING|COMPLETED|CANCELLED") String status,
            @Valid SourceRef source,
            @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_-]{0,49}") String category,
            @Size(max = 8) List<@NotNull @Valid PointActionRequest> extraActions,
            @Size(max = 500) String address) {
        public PointRequest(
                UUID stageId, String title, LocalDate scheduledOn, LocalTime scheduledTime,
                String notes, String mapsUrl, int position, String status, SourceRef source,
                String category, List<PointActionRequest> extraActions) {
            this(stageId, title, scheduledOn, scheduledTime, notes, mapsUrl, position, status,
                    source, category, extraActions, null);
        }

        public PointRequest(
                UUID stageId, String title, LocalDate scheduledOn, LocalTime scheduledTime,
                String notes, String mapsUrl, int position, String status, SourceRef source,
                String category) {
            this(stageId, title, scheduledOn, scheduledTime, notes, mapsUrl, position, status,
                    source, category, null, null);
        }

        public PointRequest(
                UUID stageId,
                String title,
                LocalDate scheduledOn,
                LocalTime scheduledTime,
                String notes,
                String mapsUrl,
                int position,
                String status,
                SourceRef source) {
            this(stageId, title, scheduledOn, scheduledTime, notes, mapsUrl, position, status,
                    source, null, null, null);
        }
    }

    public record PointDto(
            UUID id,
            UUID stageId,
            String title,
            LocalDate scheduledOn,
            LocalTime scheduledTime,
            String notes,
            String mapsUrl,
            int position,
            String status,
            SourceRef source,
            String category,
            List<PointActionDto> extraActions,
            String address) {}

    public record StayRequest(
            @NotNull UUID stageId,
            @NotBlank @Size(max = 160) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Size(max = 300) String address,
            @DecimalMin("0") @Digits(integer = 14, fraction = 4) BigDecimal price,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @Size(max = 300) String source,
            @Size(max = 1000) @SafeHttpUrl String bookingUrl,
            @Size(max = 1000) @SafeHttpUrl String mapsUrl,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            UUID photoId) {
        public StayRequest(
                UUID stageId, String name, LocalDate startsOn, LocalDate endsOn,
                String address, BigDecimal price, String currency, String source,
                String bookingUrl, String mapsUrl) {
            this(stageId, name, startsOn, endsOn, address, price, currency, source,
                    bookingUrl, mapsUrl, null, null, null);
        }
    }

    public record StayDto(
            UUID id,
            UUID stageId,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String address,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
            String currency,
            String source,
            String bookingUrl,
            String mapsUrl,
            UUID photoId,
            LocalTime checkInTime,
            LocalTime checkOutTime) {}

    public record PackingRequest(
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 160) String description,
            @Min(1) @Max(999) int quantity,
            boolean packed) {}

    public record PackingBothRequest(
            @NotBlank @Size(max = 160) String description,
            @Min(1) @Max(999) int quantity) {}

    public record PackingOrderRequest(
            @NotNull @Positive Long userId,
            @NotNull @Size(max = 1000) List<@NotNull UUID> itemIds) {}

    public record PackingDto(
            UUID id, Long userId, String description, int quantity, boolean packed, int position) {}

    public record MemberDto(Long id, String username) {}

    public record MovementRequest(
            UUID stageId,
            UUID pointId,
            UUID stayId,
            @NotBlank @Pattern(regexp = "FUNDS|EXPENSE|REFUND") String kind,
            @NotBlank @Size(max = 160) String description,
            @NotNull
                    @DecimalMin(value = "0", inclusive = false)
                    @Digits(integer = 14, fraction = 4)
                    @JsonFormat(shape = JsonFormat.Shape.STRING)
                    BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull LocalDate occurredOn) {}

    public record MovementDto(
            UUID id,
            UUID stageId,
            UUID pointId,
            UUID stayId,
            String kind,
            String description,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String currency,
            LocalDate occurredOn) {}

    public record BalanceDto(
            String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal funds,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal expenses,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal refunds,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {}

    public record ReviewRequest(
            UUID stayId, @Min(1) @Max(5) short rating, @Size(max = 2000) String comment) {}

    public record ReviewDto(
            UUID id, UUID stayId, Long userId, String author, short rating, String comment) {}

    public record FileLinksRequest(UUID stageId, UUID pointId, UUID stayId, UUID movementId) {}

    public record FileDateRequest(@NotNull Instant occurredAt) {}

    public record StageBalanceDto(UUID stageId, List<BalanceDto> balances) {}

    public record FileDto(
            UUID id,
            String name,
            String contentType,
            long byteSize,
            UUID stageId,
            UUID pointId,
            UUID stayId,
            UUID movementId,
            String url,
            String purpose,
            LocalDate day,
            Integer width,
            Integer height,
            String thumbnailUrl,
            Instant occurredAt) {}

    public record SourceDto(
            String section, Long entityId, String title, Long cityId, String href, String thumbnailUrl) {}

    public record ExperienceDto(Long id, LocalDate date, Long cityId, UUID stageId) {}

    public record DateLinkRequest(
            @NotNull UUID stageId,
            @NotNull LocalDate date,
            LocalDate endsOn,
            Long specialDateId,
            @Size(max = 160) String label) {
        public DateLinkRequest(UUID stageId, LocalDate date, Long specialDateId, String label) {
            this(stageId, date, date, specialDateId, label);
        }
    }

    public record LinkedDateDto(Long specialDateId, LocalDate date, LocalDate endsOn, String label, UUID stageId) {
        public LinkedDateDto(Long specialDateId, LocalDate date, String label, UUID stageId) {
            this(specialDateId, date, date, label, stageId);
        }
    }

    public record DetailDto(
            TripDto trip,
            List<PointDto> points,
            List<StayDto> stays,
            List<PackingDto> packing,
            List<MovementDto> movements,
            List<BalanceDto> balances,
            List<ReviewDto> reviews,
            List<FileDto> files,
            List<MemberDto> members,
            List<LinkedDateDto> dates,
            List<StageBalanceDto> stageBalances) {}

    public record JourneyPhotoDto(
            UUID id, String name, String url, String thumbnailUrl, int width, int height,
            String purpose, LocalDate day) {}

    public record JourneyDayIndexDto(LocalDate date, List<String> destinations) {}

    public record JourneyDayEntryDto(
            String id, String section, LocalDate date, String title, String detail,
            String href, List<JourneySourcePhotoDto> photos) {}

    public record JourneyGalleryEntryDto(
            LocalDate date, String section, String title, String href,
            List<JourneySourcePhotoDto> photos) {}

    public record JourneySourcePhotoDto(
            String id, String url, String thumbnailUrl, int width, int height) {}

    public record JourneySpecialDateDto(Long id, String label, String recurrence, String href) {}

    public record JourneyDayReviewDto(
            UUID id, Long userId, String author, Short rating, String comment) {}

    public record JourneyDayDto(
            LocalDate date, String story, List<JourneyDayEntryDto> entries,
            List<JourneySpecialDateDto> specialDates, List<JourneyPhotoDto> photos,
            List<JourneyDayReviewDto> reviews) {}

    public record JourneyDayStoryRequest(@Size(max = 4000) String story) {}

    public record JourneyDayReviewRequest(
            @Min(1) @Max(5) Short rating, @Size(max = 2000) String comment) {}

    public record BindingRequest(Long cityId, UUID stageId, UUID pointId) {}

    public record ExperienceLocation(Long cityId, UUID stageId, UUID pointId, UUID journeyId) {}
}
