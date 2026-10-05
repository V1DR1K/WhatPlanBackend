package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;

import com.wherefood.domain.User;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/whither-journey")
@PreAuthorize("isAuthenticated()")
public class JourneyApi {
    private final JourneyService service;
    private final JourneyDayService dayService;

    public JourneyApi(JourneyService service, JourneyDayService dayService) {
        this.service = service;
        this.dayService = dayService;
    }

    @GetMapping
    public List<TripDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) String sort) {
        return service.list(page, size, archived, search, status, destinationId, from, to, sort);
    }

    @GetMapping("/destinations")
    public List<CityDto> destinations() {
        return service.destinations();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto create(@Valid @RequestBody TripRequest request) {
        return service.saveTrip(null, request);
    }

    @GetMapping("/{id}")
    public DetailDto detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/days")
    public List<JourneyDayIndexDto> days(@PathVariable UUID id) {
        return dayService.days(id);
    }

    @GetMapping("/{id}/gallery")
    public List<JourneyGalleryEntryDto> gallery(@PathVariable UUID id) {
        return dayService.gallery(id);
    }

    @GetMapping("/{id}/days/{day}")
    public JourneyDayDto day(@PathVariable UUID id, @PathVariable java.time.LocalDate day) {
        return dayService.day(id, day);
    }

    @PutMapping("/{id}/days/{day}/story")
    public JourneyDayDto saveStory(@PathVariable UUID id, @PathVariable java.time.LocalDate day,
            @RequestBody @Valid JourneyDayStoryRequest request) {
        return dayService.saveStory(id, day, request);
    }

    @PutMapping("/{id}/days/{day}/reviews/me")
    public JourneyDayReviewDto saveDayReview(@PathVariable UUID id,
            @PathVariable java.time.LocalDate day,
            @RequestBody @Valid JourneyDayReviewRequest request,
            @AuthenticationPrincipal User actor) {
        return dayService.saveReview(id, day, request, actor);
    }

    @DeleteMapping("/{id}/days/{day}/reviews/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDayReview(@PathVariable UUID id, @PathVariable java.time.LocalDate day,
            @AuthenticationPrincipal User actor) {
        dayService.deleteReview(id, day, actor);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JourneyPhotoDto uploadPhoto(@PathVariable UUID id,
            @RequestParam(defaultValue = "TRIP") String purpose,
            @RequestParam(required = false) java.time.LocalDate day,
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        return dayService.uploadPhoto(id, purpose, day, file);
    }

    @PutMapping("/{id}/cover/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCover(@PathVariable UUID id, @PathVariable UUID fileId) {
        dayService.setCover(id, fileId);
    }

    @PutMapping("/{id}")
    public TripDto update(@PathVariable UUID id, @Valid @RequestBody TripRequest request) {
        return service.saveTrip(id, request);
    }

    @PostMapping("/{id}/dates")
    public LinkedDateDto linkDate(
            @PathVariable UUID id,
            @Valid @RequestBody DateLinkRequest request,
            @AuthenticationPrincipal User user) {
        return service.linkDate(id, request, user);
    }

    @PutMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteTrip(id);
    }

    @PutMapping("/{id}/points/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable UUID id, @RequestBody List<UUID> order) {
        service.reorder(id, order);
    }

    @PostMapping("/{id}/points")
    public PointDto addPoint(@PathVariable UUID id, @Valid @RequestBody PointRequest request) {
        return service.savePoint(id, null, request);
    }

    @PutMapping("/{id}/points/{pointId}")
    public PointDto updatePoint(
            @PathVariable UUID id,
            @PathVariable UUID pointId,
            @Valid @RequestBody PointRequest request) {
        return service.savePoint(id, pointId, request);
    }

    @DeleteMapping("/points/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoint(@PathVariable UUID id) {
        service.deletePoint(id);
    }

    @PostMapping("/{id}/stays")
    public StayDto addStay(@PathVariable UUID id, @Valid @RequestBody StayRequest request) {
        return service.saveStay(id, null, request);
    }

    @PutMapping("/{id}/stays/{stayId}")
    public StayDto updateStay(
            @PathVariable UUID id,
            @PathVariable UUID stayId,
            @Valid @RequestBody StayRequest request) {
        return service.saveStay(id, stayId, request);
    }

    @DeleteMapping("/stays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStay(@PathVariable UUID id) {
        service.deleteStay(id);
    }

    @PostMapping("/{id}/packing")
    public PackingDto addPacking(
            @PathVariable UUID id, @Valid @RequestBody PackingRequest request) {
        return service.savePacking(id, null, request);
    }

    @PostMapping("/{id}/packing/both")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PackingDto> addPackingForBoth(
            @PathVariable UUID id, @Valid @RequestBody PackingBothRequest request) {
        return service.savePackingForBoth(id, request);
    }

    @PutMapping("/{id}/packing/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderPacking(
            @PathVariable UUID id, @Valid @RequestBody PackingOrderRequest request) {
        service.reorderPacking(id, request);
    }

    @PutMapping("/{id}/packing/{itemId}")
    public PackingDto updatePacking(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody PackingRequest request) {
        return service.savePacking(id, itemId, request);
    }

    @DeleteMapping("/packing/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePacking(@PathVariable UUID id) {
        service.deletePacking(id);
    }

    @PostMapping("/{id}/movements")
    public MovementDto addMovement(
            @PathVariable UUID id, @Valid @RequestBody MovementRequest request) {
        return service.saveMovement(id, null, request);
    }

    @PutMapping("/{id}/movements/{movementId}")
    public MovementDto updateMovement(
            @PathVariable UUID id,
            @PathVariable UUID movementId,
            @Valid @RequestBody MovementRequest request) {
        return service.saveMovement(id, movementId, request);
    }

    @DeleteMapping("/movements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMovement(@PathVariable UUID id) {
        service.deleteMovement(id);
    }

    @PutMapping("/{id}/reviews/me")
    public ReviewDto review(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal User user) {
        return service.review(id, request, user);
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileDto upload(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID stageId,
            @RequestParam(required = false) UUID pointId,
            @RequestParam(required = false) UUID stayId,
            @RequestParam(required = false) UUID movementId,
            @RequestParam(defaultValue = "false") boolean hotelPhoto,
            @RequestPart("file") MultipartFile file)
            throws java.io.IOException {
        return service.upload(id, stageId, pointId, stayId, movementId, hotelPhoto, file);
    }

    @GetMapping("/files/{id}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download,
            @RequestParam(defaultValue = "false") boolean thumbnail) {
        var f = service.file(id);
        boolean photo = "TRIP".equals(f.purpose) || "DAY".equals(f.purpose);
        boolean thumbnailResponse = thumbnail && f.thumbnailContent != null;
        byte[] bytes = photo ? dayService.photoBytes(id, thumbnail)
                : thumbnailResponse ? f.thumbnailContent : f.content;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .varyBy("Authorization", "Cookie")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        "Content-Disposition",
                        (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                                .filename(f.name, java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        photo || thumbnailResponse ? "image/webp" : f.contentType))
                .body(bytes);
    }

    @PutMapping("/files/{id}/links")
    public FileDto relinkFile(@PathVariable UUID id, @RequestBody FileLinksRequest request) {
        return service.relinkFile(id, request);
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable UUID id) {
        service.deleteFile(id);
    }

    @GetMapping("/catalog/{section}")
    public List<SourceDto> catalog(
            @PathVariable String section,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String search) {
        return service.catalog(section, cityId, search);
    }

    @GetMapping("/experiences/{section}/{entityId}")
    public List<ExperienceDto> experiences(
            @PathVariable String section,
            @PathVariable Long entityId,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(defaultValue = "0") int page) {
        return service.experiences(section, entityId, from, to, page);
    }

    @GetMapping("/experiences/{section}/{entityId}/{experienceId}/location")
    public ExperienceLocation location(
            @PathVariable String section,
            @PathVariable Long entityId,
            @PathVariable Long experienceId) {
        return service.experienceLocation(section, entityId, experienceId);
    }

    @PutMapping("/experiences/{section}/{entityId}/{experienceId}/location")
    public ExperienceLocation bind(
            @PathVariable String section,
            @PathVariable Long entityId,
            @PathVariable Long experienceId,
            @RequestBody BindingRequest request) {
        return service.bind(section, entityId, experienceId, request);
    }
}
