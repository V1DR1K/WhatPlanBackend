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
            @NotEmpty @Size(max = 100) List<@Valid StageRequest> stages) {}

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
            List<StageDto> stages) {}

    public record SourceRef(
            @NotBlank @Pattern(regexp = "FOOD|FILM|COOK|FUN") String section,
            @NotNull @Positive Long entityId,
            @Positive Long experienceId) {}

    public record PointRequest(
            @NotNull UUID stageId,
            @NotBlank @Size(max = 160) String title,
            LocalDate scheduledOn,
            LocalTime scheduledTime,
            @Size(max = 4000) String notes,
            @Size(max = 1000) @SafeHttpUrl String mapsUrl,
            @Min(0) int position,
            @NotBlank @Pattern(regexp = "PENDING|COMPLETED|CANCELLED") String status,
            @Valid SourceRef source) {}

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
            SourceRef source) {}

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
            @Size(max = 1000) @SafeHttpUrl String mapsUrl) {}

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
            UUID photoId) {}

    public record PackingRequest(
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 160) String description,
            @Min(1) @Max(999) int quantity,
            boolean packed) {}

    public record PackingDto(
            UUID id, Long userId, String description, int quantity, boolean packed) {}

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
            String url) {}

    public record SourceDto(
            String section, Long entityId, String title, Long cityId, String href) {}

    public record ExperienceDto(Long id, LocalDate date, Long cityId, UUID stageId) {}

    public record DateLinkRequest(
            @NotNull UUID stageId,
            @NotNull LocalDate date,
            Long specialDateId,
            @Size(max = 160) String label) {}

    public record LinkedDateDto(Long specialDateId, LocalDate date, String label, UUID stageId) {}

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

    public record BindingRequest(Long cityId, UUID stageId, UUID pointId) {}

    public record ExperienceLocation(Long cityId, UUID stageId, UUID pointId, UUID journeyId) {}
}
