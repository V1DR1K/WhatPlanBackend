package com.wherefood.web;

import com.wherefood.config.*;
import com.wherefood.domain.*;
import com.wherefood.application.PlaceInput;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.security.core.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.server.ResponseStatusException;
import com.wherefood.validation.SafeHttpUrl;
import com.wherefood.couple.CoupleAuthorizationService;

record CategoryRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 60) String slug, @NotBlank @Size(max = 40) String icon, boolean active) {}
record CategoryDto(Long id, String name, String slug, String icon, boolean active) {}
record HighlightTagRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 20) String emoji, Boolean active) {
 HighlightTagRequest(String name, String emoji) { this(name, emoji, null); }
}
record HighlightTagDto(Long id, String name, String emoji, boolean active) {}
record PlaceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 300) String address, @Size(max = 1000) @SafeHttpUrl String sourceUrl, @Size(max = 1000) @SafeHttpUrl String mapsUrl, boolean acceptsReservations, @NotNull @Positive Long categoryId, @Size(max = 30) List<@NotNull @Positive Long> tagIds, @Positive Long zoneId, UUID stageId) {
 PlaceInput toInput() { return new PlaceInput(name, address, sourceUrl, mapsUrl, acceptsReservations, categoryId, tagIds, zoneId, stageId); }
 PlaceRequest(String name, String address, String sourceUrl, String mapsUrl, boolean acceptsReservations, Long categoryId, List<Long> tagIds) {
  this(name, address, sourceUrl, mapsUrl, acceptsReservations, categoryId, tagIds, null, null);
 }
 PlaceRequest(String name, String address, String sourceUrl, String mapsUrl, boolean acceptsReservations, Long categoryId, List<Long> tagIds, Long zoneId) { this(name, address, sourceUrl, mapsUrl, acceptsReservations, categoryId, tagIds, zoneId, null); }
}
record VisitRequest(@NotNull LocalDate visitedOn, @Positive Long cityId, UUID stageId, UUID pointId) {
 VisitRequest(LocalDate visitedOn) { this(visitedOn,null,null,null); }
}
record ItemRequest(@NotBlank @Size(max = 120) String name) {}
record ItemReviewRequest(@Size(max = 1000) String comment, @Min(1) @Max(5) short taste, @Min(1) @Max(5) short price) {}
record CreateItemRequest(@NotBlank @Size(max = 120) String name) {}
record PlaceReviewRequest(@Size(max = 2000) String comment, @Min(1) @Max(5) Short location, @Min(1) @Max(5) Short heating, @Min(1) @Max(5) Short bathrooms, @Min(1) @Max(5) Short exterior, @Min(1) @Max(5) Short seating, @Min(1) @Max(5) Short service, @Min(1) @Max(5) Short ambiance) {}
record PlaceReviewDto(String author, String comment, Short location, Short heating, Short bathrooms, Short exterior, Short seating, Short service, Short ambiance) {}
record ItemReviewDto(String author, String comment, short taste, short price, Instant createdAt, Instant updatedAt) {}
record ItemDto(Long id, String name, String createdBy, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<ItemReviewDto> reviews, Instant createdAt) {}
record ItemCatalogDto(Long id, String name, String comment, short taste, short price, String author, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, LocalDate visitDate, Instant createdAt, List<ItemReviewDto> reviews) {}
record PlaceVisitSummaryDto(Long id, LocalDate visitedOn, String createdBy, Instant createdAt, Long cityId, UUID stageId) {}
record PlaceVisitPhotoDto(Long id, String url, String thumbnailUrl, int width, int height, int position, String createdBy, Instant createdAt) {}
record PlaceVisitReviewRequest(@NotNull @Min(1) @Max(5) Short overall, @Size(max = 2000) String comment, @Min(1) @Max(5) Short taste, @Min(1) @Max(5) Short price) {}
record PlaceVisitReviewDto(Long id, String author, String updatedBy, short overall, String comment, Short taste, Short price, Instant createdAt, Instant updatedAt) {}
record PlaceVisitDto(Long id, Long placeId, LocalDate visitedOn, String createdBy, List<ItemDto> items, List<PlaceVisitPhotoDto> photos, PlaceVisitPhotoDto coverPhoto, List<PlaceVisitReviewDto> reviews, String updatedBy, Instant createdAt, Instant updatedAt, Long cityId, UUID stageId) {}
 record PlaceDto(Long id, Long zoneId, String name, String address, String sourceUrl, String mapsUrl, boolean acceptsReservations, PlaceStatus status, CategoryDto category, List<HighlightTagDto> tags, String author, double rating, double tasteAverage, double priceAverage, double venueAverage, long itemCount, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<PlaceReviewDto> reviews, Instant createdAt, Instant updatedAt) {}
record Slice<T>(List<T> content, Long nextCursor) {}

@RestController
@RequestMapping("/api")
public class Api {
 private static final int MAX_VISIT_PHOTOS = 4;
 private final Users users; private final Categories categories; private final HighlightTags highlightTags; private final Places places; private final PlaceVisits visits; private final Items items; private final Photos photos; private final ItemReviews itemReviews; private final PlaceReviews reviews; private final PlacePhotos placePhotos; private final PlaceVisitPhotos visitPhotos; private final PlaceVisitReviews visitReviews; private final PhotoStorage storage; private final PlaceVisitReviewService visitReviewService; private final PlaceVisitService visitService; private final ItemService itemService; private final ItemReviewService itemReviewService; private final PlaceReviewService placeReviewService; private final PlaceService placeService; private final PlaceMediaService mediaService; private final PlaceCatalogAdminService catalogAdminService;

   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage,
            new PlaceVisitReviewService(visitReviews, visits, new CoupleAuthorizationService(null)),
            new PlaceVisitService(places, visits, new CoupleAuthorizationService(null)),
            new ItemService(items, visits, new CoupleAuthorizationService(null)),
            new ItemReviewService(items, itemReviews, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService,
            new PlaceVisitService(places, visits, new CoupleAuthorizationService(null)),
            new ItemService(items, visits, new CoupleAuthorizationService(null)),
            new ItemReviewService(items, itemReviews, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService, visitService,
            new ItemService(items, visits, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService, visitService, itemService,
            new ItemReviewService(items, itemReviews, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService, ItemReviewService itemReviewService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService, visitService, itemService, itemReviewService,
            new PlaceReviewService(places, reviews, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService, ItemReviewService itemReviewService, PlaceReviewService placeReviewService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService, visitService, itemService, itemReviewService, placeReviewService,
            new PlaceService(places, categories, highlightTags, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService, ItemReviewService itemReviewService, PlaceReviewService placeReviewService, PlaceService placeService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos, visitPhotos, visitReviews, storage, visitReviewService, visitService, itemService, itemReviewService, placeReviewService, placeService,
            new PlaceMediaService(places, visits, items, placePhotos, visitPhotos, photos, storage, new CoupleAuthorizationService(null)));
   }
   public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService, ItemReviewService itemReviewService, PlaceReviewService placeReviewService, PlaceService placeService, PlaceMediaService mediaService) {
    this(users, categories, highlightTags, places, visits, items, photos, itemReviews, reviews, placePhotos,
            visitPhotos, visitReviews, storage, visitReviewService, visitService, itemService,
            itemReviewService, placeReviewService, placeService, mediaService,
            new PlaceCatalogAdminService(categories, highlightTags));
   }

   @org.springframework.beans.factory.annotation.Autowired public Api(Users users, Categories categories, HighlightTags highlightTags, Places places, PlaceVisits visits, Items items, Photos photos, ItemReviews itemReviews, PlaceReviews reviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, PlaceVisitReviews visitReviews, PhotoStorage storage, PlaceVisitReviewService visitReviewService, PlaceVisitService visitService, ItemService itemService, ItemReviewService itemReviewService, PlaceReviewService placeReviewService, PlaceService placeService, PlaceMediaService mediaService, PlaceCatalogAdminService catalogAdminService) {
    this.users = users; this.categories = categories; this.highlightTags = highlightTags; this.places = places; this.visits = visits; this.items = items; this.photos = photos; this.itemReviews = itemReviews; this.reviews = reviews; this.placePhotos = placePhotos; this.visitPhotos = visitPhotos; this.visitReviews = visitReviews; this.storage = storage; this.visitReviewService = visitReviewService; this.visitService = visitService; this.itemService = itemService; this.itemReviewService = itemReviewService; this.placeReviewService = placeReviewService; this.placeService = placeService; this.mediaService = mediaService;
    this.catalogAdminService = catalogAdminService;
   }
 @GetMapping("/categories") List<CategoryDto> categories() { return categories.findByActiveTrueOrderByName().stream().map(Api::category).toList(); }
 @GetMapping("/categories/all") @PreAuthorize("hasRole('ADMIN')") List<CategoryDto> allCategories() { return categories.findAll().stream().map(Api::category).toList(); }
 @PostMapping("/categories") @PreAuthorize("hasRole('ADMIN')") CategoryDto addCategory(@RequestBody @jakarta.validation.Valid CategoryRequest request) { return category(catalogAdminService.createCategory(request)); }
 @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") CategoryDto updateCategory(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CategoryRequest request) { return category(catalogAdminService.updateCategory(id, request)); }
 @DeleteMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCategory(@PathVariable Long id) { catalogAdminService.deleteCategory(id); }
 @GetMapping("/highlight-tags") List<HighlightTagDto> tags() { return highlightTags.findByActiveTrueOrderByNameAsc().stream().map(Api::tag).toList(); }
 @GetMapping("/highlight-tags/all") @PreAuthorize("hasRole('ADMIN')") List<HighlightTagDto> allTags() { return highlightTags.findAllByOrderByNameAsc().stream().map(Api::tag).toList(); }
 @PostMapping("/highlight-tags") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto addTag(@RequestBody @jakarta.validation.Valid HighlightTagRequest request) { return tag(catalogAdminService.createTag(request)); }
 @PutMapping("/highlight-tags/{id}") @PreAuthorize("hasRole('ADMIN')") HighlightTagDto updateTag(@PathVariable Long id, @RequestBody @jakarta.validation.Valid HighlightTagRequest request) { return tag(catalogAdminService.updateTag(id, request)); }
 @DeleteMapping("/highlight-tags/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteTag(@PathVariable Long id) { catalogAdminService.deleteTag(id); }

 @GetMapping("/places") Slice<PlaceDto> list(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long highlightTagId, @RequestParam(required = false) PlaceStatus status, @RequestParam(defaultValue = "ALL") ReviewStatusFilter reviewStatus, @RequestParam(required = false) String search, @RequestParam(required = false) String sort, @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "12") int size) {
   int limit = Math.max(1, Math.min(size, 30));
   long offset = cursor == null ? 0 : Math.max(0, cursor);
   if (offset > 1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
   String normalizedSearch = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
   String normalizedSort = sort == null ? "created-desc" : sort.trim().toLowerCase(Locale.ROOT);
   if (!Set.of("rating", "rating-desc", "rating-asc", "created-desc", "date", "date-desc", "date-asc").contains(normalizedSort)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orden inválido");
   }
   List<Long> ids = highlightTagId == null
           ? places.findPageIdsByCoupleId(CoupleContext.current(), zoneId, categoryId,
                   status == null ? null : status.name(), normalizedSearch, normalizedSort,
                   reviewStatus.queryValue(), limit + 1, offset)
           : places.findPageIdsByCoupleId(CoupleContext.current(), zoneId, categoryId, highlightTagId,
                   status == null ? null : status.name(), normalizedSearch, normalizedSort,
                   reviewStatus.queryValue(), limit + 1, offset);
   Long next = ids.size() > limit ? offset + limit : null;
   List<Long> pageIds = ids.stream().limit(limit).toList();
   if (pageIds.isEmpty()) return new Slice<>(List.of(), next);
   Map<Long, Place> byId = places.findActiveByIdInAndCoupleId(pageIds, CoupleContext.current()).stream()
           .collect(java.util.stream.Collectors.toMap(place -> place.id, place -> place));
   List<Place> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
   Map<Long, PlaceSummary> summaries = placeSummaries(page);
  return new Slice<>(page.stream().map(place -> place(place, summaries.get(place.id))).toList(), next);
 }

 Slice<PlaceDto> list(Long zoneId, Long categoryId, PlaceStatus status, ReviewStatusFilter reviewStatus,
         String search, String sort, Long cursor, int size) {
  return list(zoneId, categoryId, null, status, reviewStatus, search, sort, cursor, size);
 }
 Slice<PlaceDto> list(Long zoneId, Long categoryId, PlaceStatus status, String search, String sort, Long cursor, int size) {
  return list(zoneId, categoryId, status, ReviewStatusFilter.ALL, search, sort, cursor, size);
 }
  @PostMapping("/places") PlaceDto addPlace(@RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
   return place(placeService.create(request.toInput(), owner));
  }
  @PutMapping("/places/{id}") PlaceDto editPlace(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceRequest request, @AuthenticationPrincipal User owner) {
   return place(placeService.update(id, request.toInput(), owner));
  }
   @DeleteMapping("/places/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlace(@PathVariable Long id, @AuthenticationPrincipal User owner) { placeService.archive(id, owner); }
   @GetMapping("/places/archived") Slice<PlaceDto> archivedPlaces(@RequestParam(required = false) Long zoneId,
           @RequestParam(required = false) @jakarta.validation.constraints.PositiveOrZero @Max(1_000_000) Long cursor,
           @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
    int limit = Math.max(1, Math.min(size, 30));
    long offset = cursor == null ? 0 : cursor;
    if (offset < 0 || offset > 1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
    List<Long> ids = zoneId == null
            ? places.findArchivedPageIdsByCoupleId(CoupleContext.current(), limit + 1, offset)
            : places.findArchivedPageIdsByCoupleId(CoupleContext.current(), zoneId, limit + 1, offset);
    Long next = ids.size() > limit ? offset + limit : null;
    List<Long> pageIds = ids.stream().limit(limit).toList();
    if (pageIds.isEmpty()) return new Slice<>(List.of(), next);
    Map<Long, Place> byId = places.findArchivedByIdInAndCoupleId(pageIds, CoupleContext.current()).stream()
            .collect(java.util.stream.Collectors.toMap(place -> place.id, place -> place));
    List<Place> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    Map<Long, PlaceSummary> summaries = placeSummaries(page);
    return new Slice<>(page.stream().map(place -> place(place, summaries.get(place.id))).toList(), next);
   }
   Slice<PlaceDto> archivedPlaces(Long cursor, int size) { return archivedPlaces(null, cursor, size); }
   @PostMapping("/places/{id}/restore") PlaceDto restorePlace(@PathVariable Long id, @AuthenticationPrincipal User owner) { return place(placeService.restore(id, owner)); }
  @GetMapping("/places/{id}") PlaceDto getPlace(@PathVariable Long id) { Place place = active(places.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Lugar"))); return place(place, placeSummaries(List.of(place)).get(id)); }
 @GetMapping(value = "/places/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> placePhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
  active(places.findByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Lugar"))); PlacePhoto photo = placePhotos.findByPlaceIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
    return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PutMapping("/places/{id}/review") PlaceReviewDto saveReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceReviewRequest request, @AuthenticationPrincipal User author) {
  return review(placeReviewService.saveOwn(id, request, author));
 }

 @PostMapping(value = "/places/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) PlaceDto uploadPlacePhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
   return place(mediaService.uploadPlacePhoto(id, file, user));
 }

  @GetMapping("/places/{id}/visits") KeysetSlice<PlaceVisitSummaryDto> listVisits(@PathVariable Long id,
          @RequestParam(required = false) String cursor,
          @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    active(places.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Lugar")));
    int limit = Math.max(1, Math.min(size, 30));
    LocalDateIdCursor position = LocalDateIdCursor.decode(cursor);
    if (position != null && position.date() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
    UUID coupleId = CoupleContext.current();
    org.springframework.data.domain.Pageable pagination = org.springframework.data.domain.PageRequest.of(0, limit + 1);
    List<Long> ids = position == null
            ? visits.findFirstHistoryPageIdsByPlaceIdAndCoupleId(id, coupleId, pagination)
            : visits.findHistoryPageIdsAfterCursor(id, coupleId, position.date(), position.id(), pagination);
    boolean hasMore = ids.size() > limit;
    List<Long> pageIds = ids.stream().limit(limit).toList();
    Map<Long, PlaceVisit> byId = pageIds.isEmpty() ? Map.of() : visits.findAllByIdInAndPlaceIdAndCoupleId(pageIds, id, coupleId).stream().collect(java.util.stream.Collectors.toMap(value -> value.id, value -> value));
    List<PlaceVisit> page = pageIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    String next = hasMore && !page.isEmpty()
            ? new LocalDateIdCursor(page.getLast().visitedOn, page.getLast().id).encode() : null;
    return new KeysetSlice<>(page.stream().map(Api::visitSummary).toList(), next);
  }
   @GetMapping("/places/{id}/item-dates") List<LocalDate> itemDates(@PathVariable Long id) {
     active(places.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Lugar")));
     return items.findItemDatesByPlaceIdAndCoupleId(id, CoupleContext.current());
   }
    @GetMapping("/items") Slice<ItemCatalogDto> listItems(@RequestParam @Positive Long placeId, @RequestParam(required = false) LocalDate visitDate, @RequestParam(required = false) @jakarta.validation.constraints.PositiveOrZero @Max(1_000_000) Long cursor, @RequestParam(defaultValue = "30") int size) {
     active(places.findDetailedByIdAndCoupleId(placeId, CoupleContext.current()).orElseThrow(() -> notFound("Lugar")));
    int limit = Math.max(1, Math.min(size, 100));
       int offset = cursor == null ? 0 : Math.max(0, cursor.intValue());
       org.springframework.data.domain.Pageable page = org.springframework.data.domain.PageRequest.of(offset / limit, limit);
        List<Item> catalog = visitDate == null ? items.findCatalogByPlaceIdAndCoupleId(placeId, CoupleContext.current(), page) : items.findCatalogByPlaceIdAndVisitDateAndCoupleId(placeId, visitDate, CoupleContext.current(), page);
        Long next = catalog.size() == limit ? (long) offset + limit : null;
        Map<Long, ItemPhoto> catalogPhotos = catalog.isEmpty() || photos == null ? Map.of() : photos.findByItemIdInAndCoupleId(catalog.stream().map(item -> item.id).toList(), CoupleContext.current()).stream().filter(photo -> photo.item != null && photo.item.id != null).collect(java.util.stream.Collectors.toMap(photo -> photo.item.id, photo -> photo, (first, ignored) -> first));
        Map<Long, List<ItemReview>> catalogReviews = catalog.isEmpty() ? Map.of() : itemReviews.findByItemIdInAndCoupleIdOrderByItemIdAscAuthorUsername(catalog.stream().map(item -> item.id).toList(), CoupleContext.current()).stream().collect(java.util.stream.Collectors.groupingBy(review -> review.item.id));
        return new Slice<>(catalog.stream().map(item -> catalogItem(item, catalogPhotos.get(item.id), catalogReviews.getOrDefault(item.id, List.of()))).toList(), next);
   }
   @PostMapping("/places/{id}/visits") @ResponseStatus(HttpStatus.CREATED) PlaceVisitSummaryDto addVisit(@PathVariable Long id, @RequestBody @jakarta.validation.Valid VisitRequest request, @AuthenticationPrincipal User author) {
   return visitSummary(visitService.create(id, request, author));
  }
   @PutMapping("/place-visits/{id}") PlaceVisitSummaryDto editVisit(@PathVariable Long id, @RequestBody @jakarta.validation.Valid VisitRequest request, @AuthenticationPrincipal User author) {
    return visitSummary(visitService.update(id, request, author));
  }
  @DeleteMapping("/place-visits/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteVisit(@PathVariable Long id, @AuthenticationPrincipal User author) { visitService.delete(id, author); }
 @GetMapping("/place-visits/{id}") PlaceVisitDto getVisit(@PathVariable Long id) { return visit(active(visits.findDetailedByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Visita")))); }

 @PostMapping("/place-visits/{id}/items") ItemDto addItem(@PathVariable Long id, @RequestBody @jakarta.validation.Valid CreateItemRequest request, @AuthenticationPrincipal User author) {
  return item(itemService.create(id, request, author));
 }
   @PutMapping("/items/{id}") ItemDto editItem(@PathVariable Long id, @RequestBody @jakarta.validation.Valid ItemRequest request, @AuthenticationPrincipal User author) { return item(itemService.update(id, request, author)); }
   @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteItem(@PathVariable Long id, @AuthenticationPrincipal User author) { itemService.delete(id, author); }
 @PutMapping("/items/{id}/reviews/me") ItemReviewDto saveItemReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid ItemReviewRequest request, @AuthenticationPrincipal User author) {
  return itemReview(itemReviewService.saveOwn(id, request, author));
 }
  @PostMapping(value = "/items/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) ItemDto upload(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException { ItemPhoto photo = mediaService.uploadItemPhoto(id, file, author); return item(photo.item, photo); }
  @GetMapping(value = "/items/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> itemPhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   active(items.findByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Ítem"))); ItemPhoto photo = photos.findByItemIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
   return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }

  @PostMapping(value = "/place-visits/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) PlaceVisitDto uploadVisitPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   return visit(mediaService.uploadVisitPhoto(id, file, author));
  }
  @PutMapping("/place-visits/{id}/cover/{photoId}") PlaceVisitDto setVisitCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
   return visit(mediaService.setVisitCover(id, photoId, author));
  }
  @DeleteMapping("/place-visit-photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteVisitPhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
   mediaService.deleteVisitPhoto(photoId, author);
  }
  @GetMapping(value = "/place-visit-photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> visitPhoto(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
   PlaceVisitPhoto photo = visitPhotos.findByIdAndCoupleId(photoId, CoupleContext.current()).orElseThrow(() -> notFound("Foto"));
    return PrivateMediaResponse.webp(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping("/place-visits/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) PlaceVisitReviewDto addVisitReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   return visitReview(visitReviewService.create(id, request, author));
  }
  @PutMapping("/place-visits/{id}/reviews/me") PlaceVisitReviewDto saveOwnVisitReview(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   return visitReview(visitReviewService.saveOwn(id, request, author));
  }
  @PutMapping("/place-visit-reviews/{reviewId}") PlaceVisitReviewDto updateVisitReview(@PathVariable Long reviewId, @RequestBody @jakarta.validation.Valid PlaceVisitReviewRequest request, @AuthenticationPrincipal User author) {
   return visitReview(visitReviewService.update(reviewId, request, author));
  }
  @DeleteMapping("/place-visit-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteVisitReview(@PathVariable Long reviewId, @AuthenticationPrincipal User author) { visitReviewService.delete(reviewId, author); }

  private Map<Long, PlaceSummary> placeSummaries(List<Place> values) {
   if (values.isEmpty()) return Map.of();
   List<Long> placeIds = values.stream().map(place -> place.id).toList();
   List<PlaceVisit> allVisits = visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(placeIds, CoupleContext.current());
   Map<Long, List<PlaceVisit>> visitsByPlace = allVisits.stream().collect(java.util.stream.Collectors.groupingBy(visit -> visit.place.id, LinkedHashMap::new, java.util.stream.Collectors.toList()));
   List<Long> visitIds = allVisits.stream().map(visit -> visit.id).toList();
   Map<Long, List<PlaceVisitReview>> reviewsByVisit = visitIds.isEmpty() ? Map.of() : visitReviews.findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(visitIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.groupingBy(review -> review.visit.id));
   Map<Long, List<PlaceReviewDto>> reviewsByPlace = reviews.summariesByPlaceIdInAndCoupleId(placeIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.groupingBy(PlaceReviewSummary::getPlaceId, java.util.stream.Collectors.mapping(Api::review, java.util.stream.Collectors.toList())));
   Map<Long, List<PlaceVisitPhoto>> photosByVisit = visitIds.isEmpty() ? Map.of() : visitPhotos.findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(visitIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.groupingBy(photo -> photo.visit.id));
   Map<Long, PlacePhoto> legacyPhotos = placePhotos.findByPlaceIdInAndCoupleId(placeIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(photo -> photo.place.id, photo -> photo));
   Map<Long, PlaceSummary> result = new HashMap<>();
   for (Place place : values) {
    List<PlaceVisit> placeVisits = visitsByPlace.getOrDefault(place.id, List.of());
    List<PlaceVisitReview> visitReviewsForPlace = placeVisits.stream().flatMap(visit -> reviewsByVisit.getOrDefault(visit.id, List.of()).stream()).toList();
    List<PlaceReviewDto> placeReviews = reviewsByPlace.getOrDefault(place.id, List.of());
    PlaceVisitPhoto cover = placeVisits.stream().map(visit -> photosByVisit.getOrDefault(visit.id, List.of()).stream().filter(photo -> photo.id.equals(visit.coverPhotoId)).findFirst().orElseGet(() -> photosByVisit.getOrDefault(visit.id, List.of()).stream().findFirst().orElse(null))).filter(Objects::nonNull).findFirst().orElse(null);
    double taste = visitReviewsForPlace.stream().map(review -> review.taste).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    double price = visitReviewsForPlace.stream().map(review -> review.price).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    double venue = placeReviews.stream().flatMap(review -> java.util.stream.Stream.of(review.location(), review.heating(), review.bathrooms(), review.exterior(), review.seating(), review.service(), review.ambiance())).filter(Objects::nonNull).mapToInt(Short::intValue).average().orElse(0);
    double rating = java.util.stream.Stream.of(taste, price, venue).filter(value -> value > 0).mapToDouble(Double::doubleValue).average().orElse(0);
    result.put(place.id, new PlaceSummary(rating, taste, price, venue, placeVisits.size(), cover, legacyPhotos.get(place.id), placeReviews));
   }
   return result;
  }
  private PlaceDto place(Place place) { return place(place, placeSummaries(List.of(place)).get(place.id)); }
  private PlaceDto place(Place place, PlaceSummary summary) {
    PlaceVisitPhoto cover = summary.cover(); PlacePhoto profilePhoto = summary.legacyPhoto();
    String photoUrl = profilePhoto != null ? photoUrl(place.id, false, profilePhoto.id) : cover == null ? null : visitPhotoUrl(cover.id, false);
    String thumbnailUrl = profilePhoto != null ? photoUrl(place.id, true, profilePhoto.id) : cover == null ? null : visitPhotoUrl(cover.id, true);
    Integer width = profilePhoto != null ? Integer.valueOf(profilePhoto.width) : cover == null ? null : Integer.valueOf(cover.width);
    Integer height = profilePhoto != null ? Integer.valueOf(profilePhoto.height) : cover == null ? null : Integer.valueOf(cover.height);
     return new PlaceDto(place.id, place.zoneId, place.name, place.address, place.sourceUrl, place.mapsUrl, place.acceptsReservations, place.status, category(place.category), place.highlightTags.stream().sorted(Comparator.comparing(tag -> tag.name)).map(Api::tag).toList(), place.createdBy.username, round(summary.rating()), round(summary.taste()), round(summary.price()), round(summary.venue()), summary.visitCount(), photoUrl, thumbnailUrl, width, height, summary.reviews(), place.createdAt, place.updatedAt);
  }
  private record PlaceSummary(double rating, double taste, double price, double venue, long visitCount, PlaceVisitPhoto cover, PlacePhoto legacyPhoto, List<PlaceReviewDto> reviews) {}
  private static String photoUrl(Long placeId, boolean thumbnail, Long photoId) { return "/places/" + placeId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static String visitPhotoUrl(Long photoId, boolean thumbnail) { return "/place-visit-photos/" + photoId + (thumbnail ? "?thumbnail=true" : ""); }
 private static double round(double value) { return Math.round(value * 10) / 10d; }
  private PlaceVisitDto visit(PlaceVisit visit) { return visit(visit, visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(visit.id, CoupleContext.current())); }
  private PlaceVisitDto visit(PlaceVisit visit, List<PlaceVisitPhoto> currentPhotos) {
   List<Item> visitItems = items.findByVisitIdAndCoupleIdAndDeletedAtIsNullOrderByIdDesc(visit.id, CoupleContext.current());
   List<Long> itemIds = visitItems.stream().map(item -> item.id).toList();
   Map<Long, String> itemReviewAuthors = itemIds.isEmpty() ? Map.of() : itemReviews.authorsByItemIdInAndCoupleId(itemIds, CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   Map<Long, ItemPhoto> photoMap = photos.findByItemIdInAndCoupleId(visitItems.stream().map(item -> item.id).toList(), CoupleContext.current()).stream().filter(photo -> photo.item != null && photo.item.id != null).collect(java.util.stream.Collectors.toMap(photo -> photo.item.id, photo -> photo, (first, ignored) -> first));
   List<PlaceVisitPhotoDto> resultPhotos = currentPhotos.stream().map(Api::visitPhoto).toList();
   PlaceVisitPhotoDto cover = resultPhotos.stream().filter(photo -> photo.id().equals(visit.coverPhotoId)).findFirst().orElse(resultPhotos.isEmpty() ? null : resultPhotos.getFirst());
   List<PlaceVisitReview> reviewValues = visitReviews.findByVisitIdAndCoupleIdOrderByAuthorUsername(visit.id, CoupleContext.current());
   Map<Long, String> reviewAuthors = visitReviews.authorsByVisitIdInAndCoupleId(List.of(visit.id), CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor));
   List<PlaceVisitReviewDto> currentReviews = reviewValues.stream().map(review -> visitReview(review, reviewAuthors.get(review.id))).toList();
    return new PlaceVisitDto(visit.id, visit.place.id, visit.visitedOn, visit.createdBy.username, visitItems.stream().map(item -> item(item, photoMap.get(item.id), itemReviewAuthors)).toList(), resultPhotos, cover, currentReviews, visit.updatedBy.username, visit.createdAt, visit.updatedAt, visit.cityId, visit.stageId);
  }
  private ItemDto item(Item item) { return item(item, photos.findByItemIdAndCoupleId(item.id, CoupleContext.current()).orElse(null)); }
  private ItemDto item(Item item, ItemPhoto photo) { return item(item, photo, itemReviews.authorsByItemIdInAndCoupleId(List.of(item.id), CoupleContext.current()).stream().collect(java.util.stream.Collectors.toMap(ReviewAuthor::getReviewId, ReviewAuthor::getAuthor))); }
    private ItemCatalogDto catalogItem(Item item) { return catalogItem(item, photos.findByItemIdAndCoupleId(item.id, CoupleContext.current()).orElse(null), item.reviews); }
    private ItemCatalogDto catalogItem(Item item, ItemPhoto photo, List<ItemReview> reviews) {
      ItemReview review = reviews.stream().findFirst().orElse(null);
      return new ItemCatalogDto(item.id, item.name, review == null ? null : review.comment, review == null ? (short) 0 : review.taste, review == null ? (short) 0 : review.price, item.createdBy.username, photo == null ? null : itemPhotoUrl(item.id, false, photo.id), photo == null ? null : itemPhotoUrl(item.id, true, photo.id), photo == null ? null : photo.width, photo == null ? null : photo.height, item.visit.visitedOn, item.createdAt, reviews.stream().sorted(Comparator.comparing(value -> value.author.username, String.CASE_INSENSITIVE_ORDER)).map(Api::itemReview).toList());
  }
 private ItemDto item(Item item, ItemPhoto photo, Map<Long, String> reviewAuthors) { return new ItemDto(item.id, item.name, item.createdBy.username, photo == null ? null : itemPhotoUrl(item.id, false, photo.id), photo == null ? null : itemPhotoUrl(item.id, true, photo.id), photo == null ? null : Integer.valueOf(photo.width), photo == null ? null : Integer.valueOf(photo.height), item.reviews.stream().sorted(Comparator.comparing(review -> reviewAuthors.get(review.id), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).map(review -> itemReview(review, reviewAuthors.get(review.id))).toList(), item.createdAt); }
  private static String itemPhotoUrl(Long itemId, boolean thumbnail, Long photoId) { return "/items/" + itemId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
  private static PlaceVisitSummaryDto visitSummary(PlaceVisit visit) { return new PlaceVisitSummaryDto(visit.id, visit.visitedOn, visit.createdBy.username, visit.createdAt, visit.cityId, visit.stageId); }
  private static ItemReviewDto itemReview(ItemReview review) { return itemReview(review, review.author.username); }
  private static ItemReviewDto itemReview(ItemReview review, String author) { return new ItemReviewDto(author, review.comment, review.taste, review.price, review.createdAt, review.updatedAt); }
  private static PlaceVisitPhotoDto visitPhoto(PlaceVisitPhoto photo) { return new PlaceVisitPhotoDto(photo.id, "/place-visit-photos/" + photo.id, "/place-visit-photos/" + photo.id + "?thumbnail=true", photo.width, photo.height, photo.position, photo.createdBy.username, photo.createdAt); }
  private static PlaceVisitReviewDto visitReview(PlaceVisitReview review) { return visitReview(review, review.author.username); }
  private static PlaceVisitReviewDto visitReview(PlaceVisitReview review, String author) { return new PlaceVisitReviewDto(review.id, author, review.updatedBy.username, review.overall, review.comment, review.taste, review.price, review.createdAt, review.updatedAt); }
  private static PlaceReviewDto review(PlaceReview review) { return new PlaceReviewDto(review.author.username, review.comment, review.location, review.heating, review.bathrooms, review.exterior, review.seating, review.service, review.ambiance); }
  private static PlaceReviewDto review(PlaceReviewSummary review) { return new PlaceReviewDto(review.getAuthor(), review.getComment(), review.getLocation(), review.getHeating(), review.getBathrooms(), review.getExterior(), review.getSeating(), review.getService(), review.getAmbiance()); }
 private static CategoryDto category(Category category) { return new CategoryDto(category.id, category.name, category.slug, category.icon, category.active); }
 private static HighlightTagDto tag(HighlightTag tag) { return new HighlightTagDto(tag.id, tag.name, tag.emoji, tag.active); }
  private static void apply(ItemReview review, ItemReviewRequest request) { review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment(); review.taste = request.taste(); review.price = request.price(); }
  private static void apply(PlaceVisitReview review, PlaceVisitReviewRequest request) { review.overall = request.overall(); review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment(); review.taste = request.taste(); review.price = request.price(); }
  private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado"); }
 private static Place active(Place place) { if (place.deactivatedAt != null) throw notFound("Lugar"); return place; }
  private static PlaceVisit active(PlaceVisit visit) { active(visit.place); return visit; }
  private static Item active(Item item) { active(item.visit.place); return item; }
  private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
