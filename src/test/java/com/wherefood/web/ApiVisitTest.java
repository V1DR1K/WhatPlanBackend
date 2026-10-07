package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.PlaceVisitReview;
import com.wherefood.domain.PlaceStatus;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.PlacePhotos;
import com.wherefood.repo.Repositories.PlaceReviews;
import com.wherefood.repo.Repositories.PlaceReviewSummary;
import com.wherefood.repo.Repositories.PlaceVisitPhotos;
import com.wherefood.repo.Repositories.PlaceVisitReviews;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.Photos;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiVisitTest {
  @AfterEach
  void clearCoupleContext() { CoupleContext.clear(); }

  @Test
  void pagesVisitSummariesWithinTheirPlaceAndActiveCouple() {
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class);
    User author = user(7L, "tomas"); Place place = place(4L, author, Instant.parse("2026-07-23T00:00:00Z"));
    when(places.findDetailedByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place));
    when(visits.findFirstHistoryPageIdsByPlaceIdAndCoupleId(4L, coupleId,
            org.springframework.data.domain.PageRequest.of(0, 3))).thenReturn(List.of(12L, 11L, 10L));
    PlaceVisit newest = visit(12L, place, author, LocalDate.of(2026, 7, 23));
    PlaceVisit next = visit(11L, place, author, LocalDate.of(2026, 7, 22));
    when(visits.findAllByIdInAndPlaceIdAndCoupleId(List.of(12L, 11L), 4L, coupleId)).thenReturn(List.of(next, newest));

    Api api = new Api(null, null, null, places, visits, null, null, null,
            null, null, null, null, null);
    KeysetSlice<PlaceVisitSummaryDto> result = api.listVisits(4L, null, 2);

    assertEquals(List.of(12L, 11L), result.content().stream().map(PlaceVisitSummaryDto::id).toList());
    assertEquals(new LocalDateIdCursor(LocalDate.of(2026, 7, 22), 11L).encode(), result.nextCursor());
    verify(visits).findFirstHistoryPageIdsByPlaceIdAndCoupleId(4L, coupleId,
            org.springframework.data.domain.PageRequest.of(0, 3));
    verify(visits).findAllByIdInAndPlaceIdAndCoupleId(List.of(12L, 11L), 4L, coupleId);

    when(visits.findHistoryPageIdsAfterCursor(4L, coupleId, LocalDate.of(2026, 7, 22), 11L,
            org.springframework.data.domain.PageRequest.of(0, 3))).thenReturn(List.of(10L));
    PlaceVisit oldest = visit(10L, place, author, LocalDate.of(2026, 7, 21));
    when(visits.findAllByIdInAndPlaceIdAndCoupleId(List.of(10L), 4L, coupleId)).thenReturn(List.of(oldest));
    KeysetSlice<PlaceVisitSummaryDto> secondPage = api.listVisits(4L, result.nextCursor(), 2);
    assertEquals(List.of(10L), secondPage.content().stream().map(PlaceVisitSummaryDto::id).toList());
    assertNull(secondPage.nextCursor());
    verify(visits).findHistoryPageIdsAfterCursor(4L, coupleId, LocalDate.of(2026, 7, 22), 11L,
            org.springframework.data.domain.PageRequest.of(0, 3));
  }

  @Test
  void rejectsNullDateCursorForNonNullablePlaceVisitHistory() {
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class);
    User author = user(7L, "tomas");
    when(places.findDetailedByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place(4L, author, Instant.now())));
    Api api = new Api(null, null, null, places, visits, null, null, null,
            null, null, null, null, null);

    ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> api.listVisits(4L, new LocalDateIdCursor(null, 10L).encode(), 10));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    verify(visits, org.mockito.Mockito.never()).findFirstHistoryPageIdsByPlaceIdAndCoupleId(
            any(), any(), any());
    verify(visits, org.mockito.Mockito.never()).findHistoryPageIdsAfterCursor(any(), any(), any(), any(), any());
  }

  @Test
  void clampsVisitHistoryPageSizeBeforeQuerying() {
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class);
    User author = user(7L, "tomas");
    when(places.findDetailedByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place(4L, author, Instant.now())));
    Api api = new Api(null, null, null, places, visits, null, null, null,
            null, null, null, null, null);

    KeysetSlice<PlaceVisitSummaryDto> result = api.listVisits(4L, null, 0);

    assertTrue(result.content().isEmpty());
    assertNull(result.nextCursor());
    verify(visits).findFirstHistoryPageIdsByPlaceIdAndCoupleId(4L, coupleId,
            org.springframework.data.domain.PageRequest.of(0, 2));
  }

  @Test
  void returnsThePlaceToPendingAfterDeletingItsLastVisit() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    User tomas = new User();
    tomas.id = 7L;
    tomas.role = com.wherefood.domain.Role.USER;
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    CoupleMembers members = mock(CoupleMembers.class);
    when(members.findActiveCoupleIdByUserId(tomas.id)).thenReturn(Optional.of(coupleId));
    Place place = new Place();
    place.id = 4L;
    place.status = PlaceStatus.REVIEWED;
    PlaceVisit visit = new PlaceVisit();
    visit.id = 9L;
    visit.place = place;
    visit.createdBy = tomas;
    when(visits.findDetailedByIdAndCoupleId(9L, coupleId)).thenReturn(Optional.of(visit));
    when(visits.existsByPlaceIdAndCoupleId(4L, coupleId)).thenReturn(false);

    CoupleAuthorizationService authorization = new CoupleAuthorizationService(members);
    apiForVisitMutations(places, visits, authorization).deleteVisit(9L, tomas);

    assertEquals(PlaceStatus.PENDING, place.status);
    verify(visits).delete(visit);
    verify(visits).existsByPlaceIdAndCoupleId(4L, coupleId);
    verify(places).save(place);
  }

  @Test
  void scopesDuplicateVisitCheckWhenCreatingVisit() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    CoupleMembers members = mock(CoupleMembers.class);
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    User tomas = user(7L, "tomas");
    tomas.role = com.wherefood.domain.Role.USER;
    when(members.findActiveCoupleIdByUserId(tomas.id)).thenReturn(Optional.of(coupleId));
    Place place = place(4L, tomas, Instant.now());
    when(places.findByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place));
    LocalDate visitedOn = LocalDate.of(2026, 7, 12);
    when(visits.findByPlaceIdAndVisitedOnAndCoupleId(4L, visitedOn, coupleId)).thenReturn(Optional.empty());
    when(visits.save(any(PlaceVisit.class))).thenAnswer(invocation -> invocation.getArgument(0));

    apiForVisitMutations(places, visits, new CoupleAuthorizationService(members))
            .addVisit(4L, new VisitRequest(visitedOn), tomas);

    verify(visits).findByPlaceIdAndVisitedOnAndCoupleId(4L, visitedOn, coupleId);
  }

  @Test
  void preservesLineBreaksInAPlaceVisitReview() {
    Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class); PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    CoupleMembers members = mock(CoupleMembers.class); UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    User tomas = user(7L, "tomas"); Place place = place(4L, tomas, Instant.parse("2026-07-23T00:00:00Z")); place.status = PlaceStatus.REVIEWED;
    PlaceVisit visit = visit(10L, place, tomas, LocalDate.of(2026, 7, 22));
    tomas.role = com.wherefood.domain.Role.USER;
    when(members.findActiveCoupleIdByUserId(7L)).thenReturn(Optional.of(coupleId));
    when(visits.findDetailedByIdAndCoupleId(10L, coupleId)).thenReturn(Optional.of(visit));
    when(visitReviews.save(any(PlaceVisitReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PlaceVisitReviewService reviewService = new PlaceVisitReviewService(visitReviews, visits, new CoupleAuthorizationService(members));
    PlaceVisitReviewDto result = new Api(null, null, null, places, visits, null, null, null, null, null, null, visitReviews, null, reviewService).addVisitReview(10L, new PlaceVisitReviewRequest((short) 5, "Primera línea\n\nSegunda línea\n", (short) 4, null), tomas);

    assertEquals("Primera línea\n\nSegunda línea\n", result.comment());
  }

  @Test
  void updatesTheParentPlaceWhenAVisitChanges() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    User tomas = user(7L, "tomas");
    tomas.role = com.wherefood.domain.Role.USER;
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    CoupleMembers members = mock(CoupleMembers.class);
    when(members.findActiveCoupleIdByUserId(tomas.id)).thenReturn(Optional.of(coupleId));
    Place place = new Place(); place.id = 4L; place.status = PlaceStatus.REVIEWED; place.updatedAt = LocalDate.of(2026, 7, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    PlaceVisit visit = visit(9L, place, tomas, LocalDate.of(2026, 7, 10));
    when(visits.findDetailedByIdAndCoupleId(9L, coupleId)).thenReturn(Optional.of(visit));
    when(visits.findByPlaceIdAndVisitedOnAndCoupleId(4L, LocalDate.of(2026, 7, 12), coupleId)).thenReturn(Optional.empty());
    when(visits.save(visit)).thenReturn(visit);

    apiForVisitMutations(places, visits, new CoupleAuthorizationService(members)).editVisit(9L, new VisitRequest(LocalDate.of(2026, 7, 12)), tomas);

    assertEquals(LocalDate.of(2026, 7, 12), visit.visitedOn);
    assertEquals(tomas, place.updatedBy);
    verify(visits).findByPlaceIdAndVisitedOnAndCoupleId(4L, LocalDate.of(2026, 7, 12), coupleId);
    verify(places).save(place);
  }

  @Test
  void listsPlacesByTheirLatestModificationByDefault() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlaceReviews placeReviews = mock(PlaceReviews.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    User tomas = user(7L, "tomas");
    Place older = place(1L, tomas, Instant.parse("2026-07-21T00:00:00Z"));
    Place recent = place(2L, tomas, Instant.parse("2026-07-23T00:00:00Z"));
    when(places.findPageIdsByCoupleId(null, null, null, (String) null, null, "created-desc", null, 6, 0)).thenReturn(List.of(2L, 1L));
    when(places.findActiveByIdInAndCoupleId(List.of(2L, 1L), null)).thenReturn(List.of(recent, older));
    when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(any(), isNull())).thenReturn(List.of());
    when(visitReviews.findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(any(), isNull())).thenReturn(List.of());
    when(placeReviews.summariesByPlaceIdInAndCoupleId(any(), isNull())).thenReturn(List.of());
    when(visitPhotos.findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(any(), isNull())).thenReturn(List.of());
    when(placePhotos.findByPlaceIdInAndCoupleId(any(), isNull())).thenReturn(List.of());

    Slice<PlaceDto> result = new Api(null, null, null, places, visits, null, null, null, placeReviews, placePhotos, visitPhotos, visitReviews, null).list(null, null, null, ReviewStatusFilter.ALL, null, null, null, 5);

    assertEquals(List.of(2L, 1L), result.content().stream().map(PlaceDto::id).toList());
  }

  @Test
  void scopesPlaceItemDatesToTheAuthenticatedCouple() {
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    Places places = mock(Places.class); Items items = mock(Items.class);
    User author = user(7L, "tomas");
    when(places.findDetailedByIdAndCoupleId(4L, coupleId)).thenReturn(Optional.of(place(4L, author, Instant.now())));
    List<LocalDate> dates = List.of(LocalDate.of(2026, 7, 23));
    when(items.findItemDatesByPlaceIdAndCoupleId(4L, coupleId)).thenReturn(dates);
    Api api = new Api(null, null, null, places, null, items, null, null, null, null, null, null, null);

    assertEquals(dates, api.itemDates(4L));
    verify(items).findItemDatesByPlaceIdAndCoupleId(4L, coupleId);
  }

  @Test
  void queriesFilteredPlacePageWithinCurrentCouple() {
    Places places = mock(Places.class);
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    User tomas = user(7L, "tomas");
    Place place = place(31L, tomas, Instant.parse("2026-07-23T00:00:00Z"));
    when(places.findPageIdsByCoupleId(coupleId, 4L, 9L, "REVIEWED", "café", "rating-desc", null, 2, 5))
            .thenReturn(List.of(31L, 32L));
    when(places.findActiveByIdInAndCoupleId(List.of(31L), coupleId)).thenReturn(List.of(place));

    Api api = new Api(null, null, null, places, mock(PlaceVisits.class), null, null, null,
            mock(PlaceReviews.class), mock(PlacePhotos.class), mock(PlaceVisitPhotos.class),
            mock(PlaceVisitReviews.class), null);
    Slice<PlaceDto> result = api.list(4L, 9L, PlaceStatus.REVIEWED, ReviewStatusFilter.ALL, " Café ", "rating-desc", 5L, 1);

    assertEquals(List.of(31L), result.content().stream().map(PlaceDto::id).toList());
    assertEquals(6L, result.nextCursor());
    verify(places).findPageIdsByCoupleId(coupleId, 4L, 9L, "REVIEWED", "café", "rating-desc", null, 2, 5);
    verify(places).findActiveByIdInAndCoupleId(List.of(31L), coupleId);
  }

 @Test
 void filtersPlacesBySelectedZone() {
  Places places = mock(Places.class);
  PlaceVisits visits = mock(PlaceVisits.class);
  PlaceReviews reviews = mock(PlaceReviews.class);
  PlacePhotos placePhotos = mock(PlacePhotos.class);
  UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
  User tomas = user(7L, "tomas");
  Place place = place(33L, tomas, Instant.parse("2026-07-23T00:00:00Z"));
  place.zoneId = 2L;
  when(places.findPageIdsByCoupleId(coupleId, 2L, null, (String) null, null, "date-desc", null, 6, 0))
          .thenReturn(List.of(33L));
  when(places.findActiveByIdInAndCoupleId(List.of(33L), coupleId)).thenReturn(List.of(place));
  when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(33L), coupleId)).thenReturn(List.of());
  when(reviews.summariesByPlaceIdInAndCoupleId(List.of(33L), coupleId)).thenReturn(List.of());
  when(placePhotos.findByPlaceIdInAndCoupleId(List.of(33L), coupleId)).thenReturn(List.of());

  Api api = new Api(null, null, null, places, visits, null, null, null,
          reviews, placePhotos, mock(PlaceVisitPhotos.class),
          mock(PlaceVisitReviews.class), null);

  Slice<PlaceDto> result = api.list(2L, null, null, null, null, null, null, 5);

  assertEquals(List.of(33L), result.content().stream().map(PlaceDto::id).toList());
  assertEquals(2L, result.content().getFirst().zoneId());
  verify(places).findPageIdsByCoupleId(coupleId, 2L, null, (String) null, null, "date-desc", null, 6, 0);
 }

  @Test
  void pagesArchivedPlacesWithinCurrentCoupleAndKeepsStableNextCursor() {
    UUID coupleId = UUID.randomUUID(); CoupleContext.set(coupleId);
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlaceReviews placeReviews = mock(PlaceReviews.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    User owner = user(41L, "member");
    Place archived = place(70L, owner, Instant.parse("2026-07-23T00:00:00Z"));
    archived.deactivatedAt = Instant.parse("2026-07-24T00:00:00Z");
    when(places.findArchivedPageIdsByCoupleId(coupleId, 2, 3)).thenReturn(List.of(70L, 69L));
    when(places.findArchivedByIdInAndCoupleId(List.of(70L), coupleId)).thenReturn(List.of(archived));
    when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(70L), coupleId)).thenReturn(List.of());
    when(visitReviews.findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(any(), eq(coupleId))).thenReturn(List.of());
    when(placeReviews.summariesByPlaceIdInAndCoupleId(List.of(70L), coupleId)).thenReturn(List.of());
    when(visitPhotos.findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(any(), eq(coupleId))).thenReturn(List.of());
    when(placePhotos.findByPlaceIdInAndCoupleId(List.of(70L), coupleId)).thenReturn(List.of());

    Slice<PlaceDto> result = new Api(null, null, null, places, visits, null, null, null,
            placeReviews, placePhotos, visitPhotos, visitReviews, null)
            .archivedPlaces(3L, 1);

    assertEquals(List.of(70L), result.content().stream().map(PlaceDto::id).toList());
    assertEquals(4L, result.nextCursor());
    verify(places).findArchivedPageIdsByCoupleId(coupleId, 2, 3);
    verify(places).findArchivedByIdInAndCoupleId(List.of(70L), coupleId);
  }

  @Test
  void derivesPlaceCardsFromVisitReviewsAndTheLatestVisitCover() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlaceReviews placeReviews = mock(PlaceReviews.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    User tomas = user(7L, "tomas");
    User avril = user(6L, "avril");
    Place place = new Place();
    place.id = 4L; place.name = "Lugar"; place.status = PlaceStatus.REVIEWED; place.acceptsReservations = true; place.createdBy = tomas; place.createdAt = Instant.parse("2026-07-01T00:00:00Z"); place.updatedAt = Instant.parse("2026-07-22T00:00:00Z"); place.category = new com.wherefood.domain.Category();
    PlaceVisit recent = visit(10L, place, tomas, LocalDate.of(2026, 7, 22));
    PlaceVisit older = visit(9L, place, tomas, LocalDate.of(2026, 7, 15));
    PlaceVisitPhoto photo = new PlaceVisitPhoto();
    photo.id = 99L; photo.visit = recent; photo.createdBy = tomas; photo.width = 1200; photo.height = 800; recent.coverPhotoId = photo.id;
    PlaceVisitReview first = review(recent, tomas, (short) 4, (short) 3, null);
    PlaceVisitReview second = review(older, avril, (short) 5, null, (short) 4);
    PlaceReviewSummary placeReview = placeReview(place.id, tomas.username, (short) 2, (short) 4);
    when(places.findDetailedByIdAndCoupleId(4L, null)).thenReturn(Optional.of(place));
    when(places.findPageIdsByCoupleId(null, null, null, (String) null, null, "created-desc", null, 13, 0)).thenReturn(List.of(4L));
    when(places.findActiveByIdInAndCoupleId(List.of(4L), null)).thenReturn(List.of(place));
    when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(4L), null)).thenReturn(List.of(recent, older));
    when(visitPhotos.findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(List.of(10L, 9L), null)).thenReturn(List.of(photo));
    when(visitReviews.findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(List.of(10L, 9L), null)).thenReturn(List.of(first, second));
    when(placeReviews.summariesByPlaceIdInAndCoupleId(List.of(4L), null)).thenReturn(List.of(placeReview));
    when(placePhotos.findByPlaceIdInAndCoupleId(List.of(4L), null)).thenReturn(List.of());

    Api api = new Api(null, null, null, places, visits, null, null, null, placeReviews, placePhotos, visitPhotos, visitReviews, null);
    PlaceDto result = api.getPlace(4L);
    PlaceDto listed = api.list(null, null, null, ReviewStatusFilter.ALL, null, null, null, 12).content().getFirst();

    assertEquals(3.3, result.rating());
    assertEquals(3, result.tasteAverage());
    assertEquals(4, result.priceAverage());
    assertEquals(3, result.venueAverage());
    assertEquals(2, result.itemCount());
    assertTrue(result.acceptsReservations());
    assertEquals("/place-visit-photos/99", result.photoUrl());
    assertEquals("/place-visit-photos/99?thumbnail=true", result.thumbnailUrl());
    assertEquals("tomas", result.reviews().getFirst().author());
   assertEquals(result.rating(), listed.rating());
    assertEquals(Instant.parse("2026-07-22T00:00:00Z"), listed.updatedAt());
  }

  @Test
  void givesTheParentPlaceProfilePriorityOverTheVisitCover() {
   Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class); PlaceReviews placeReviews = mock(PlaceReviews.class); PlacePhotos placePhotos = mock(PlacePhotos.class);
   User tomas = user(7L, "tomas"); Place place = new Place(); place.id = 4L; place.name = "Lugar"; place.status = PlaceStatus.REVIEWED; place.createdBy = tomas; place.category = new com.wherefood.domain.Category();
   PlaceVisit visit = visit(10L, place, tomas, LocalDate.of(2026, 7, 22)); PlaceVisitPhoto cover = new PlaceVisitPhoto(); cover.id = 99L; cover.visit = visit; cover.createdBy = tomas; cover.width = 1200; cover.height = 800; visit.coverPhotoId = cover.id;
   com.wherefood.domain.PlacePhoto profile = new com.wherefood.domain.PlacePhoto(); profile.id = 88L; profile.place = place; profile.width = 900; profile.height = 600;
   when(places.findDetailedByIdAndCoupleId(4L, null)).thenReturn(Optional.of(place)); when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(4L), null)).thenReturn(List.of(visit)); when(visitPhotos.findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(List.of(10L), null)).thenReturn(List.of(cover)); when(visitReviews.findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(List.of(10L), null)).thenReturn(List.of()); when(placeReviews.summariesByPlaceIdInAndCoupleId(List.of(4L), null)).thenReturn(List.of()); when(placePhotos.findByPlaceIdInAndCoupleId(List.of(4L), null)).thenReturn(List.of(profile));

   PlaceDto result = new Api(null, null, null, places, visits, null, null, null, placeReviews, placePhotos, visitPhotos, visitReviews, null).getPlace(4L);

   assertEquals("/places/4/photo?v=88", result.photoUrl());
   assertEquals("/places/4/photo?thumbnail=true&v=88", result.thumbnailUrl());
   assertEquals(900, result.photoWidth());
   assertEquals(600, result.photoHeight());
  }

  @Test
  void keepsPhotoDimensionsEmptyWhenAPlaceHasNoMedia() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlaceReviews placeReviews = mock(PlaceReviews.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    Place place = new Place();
    place.id = 5L; place.name = "Sin foto"; place.status = PlaceStatus.PENDING; place.createdBy = user(7L, "tomas"); place.category = new com.wherefood.domain.Category();
    when(places.findDetailedByIdAndCoupleId(5L, null)).thenReturn(Optional.of(place));
    when(visits.findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(5L), null)).thenReturn(List.of());
    when(placeReviews.summariesByPlaceIdInAndCoupleId(List.of(5L), null)).thenReturn(List.of());
    when(placePhotos.findByPlaceIdInAndCoupleId(List.of(5L), null)).thenReturn(List.of());

    PlaceDto result = new Api(null, null, null, places, visits, null, null, null, placeReviews, placePhotos, visitPhotos, visitReviews, null).getPlace(5L);

    assertNull(result.photoUrl());
    assertNull(result.photoWidth());
    assertNull(result.photoHeight());
  }

  @Test
  void returnsTheUploadedVisitPhotoWithoutRequeryingHibernateGraph() throws Exception {
   Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class); Items items = mock(Items.class); Photos itemPhotos = mock(Photos.class); PlaceReviews placeReviews = mock(PlaceReviews.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class); PlaceMediaService media = mock(PlaceMediaService.class);
   User tomas = user(7L, "tomas"); Place place = new Place(); place.id = 4L; place.status = PlaceStatus.REVIEWED; place.createdBy = tomas;
   PlaceVisit visit = visit(10L, place, tomas, LocalDate.of(2026, 7, 22)); PlaceVisitPhoto photo = new PlaceVisitPhoto(); photo.id = 99L; photo.visit = visit; photo.createdBy = tomas; photo.width = 1200; photo.height = 800;
   when(media.uploadVisitPhoto(eq(10L), any(), same(tomas))).thenReturn(visit); when(visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(10L, null)).thenReturn(List.of(photo)); when(items.findByVisitIdAndCoupleIdAndDeletedAtIsNullOrderByIdDesc(10L, null)).thenReturn(List.of()); when(itemPhotos.findByItemIdInAndCoupleId(List.of(), null)).thenReturn(List.of()); when(visitReviews.findByVisitIdAndCoupleIdOrderByAuthorUsername(10L, null)).thenReturn(List.of());

   PlaceVisitDto result = apiWithMedia(places, visits, items, itemPhotos, placeReviews, placePhotos, visitPhotos, visitReviews, media).uploadVisitPhoto(10L, new MockMultipartFile("file", "foto.webp", "image/webp", new byte[] {1}), tomas);

   assertEquals(99L, result.coverPhoto().id());
   assertEquals(1, result.photos().size());
   verify(media).uploadVisitPhoto(eq(10L), any(), same(tomas));
  }

  @Test
  void rejectsAVisitPhotoBeyondTheFourPhotoLimit() throws Exception {
    Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); PlaceMediaService media = mock(PlaceMediaService.class);
    User tomas = user(7L, "tomas");
    when(media.uploadVisitPhoto(eq(10L), any(), same(tomas))).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

    ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> apiWithMedia(places, visits, null, null, null, null, visitPhotos, null, media).uploadVisitPhoto(10L, new MockMultipartFile("file", "foto.webp", "image/webp", new byte[] {1}), tomas));

    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
  }

  private static PlaceVisit visit(Long id, Place place, User author, LocalDate visitedOn) {
    PlaceVisit visit = new PlaceVisit();
    visit.id = id; visit.place = place; visit.createdBy = visit.updatedBy = author; visit.visitedOn = visitedOn; return visit;
  }

  private static Place place(Long id, User author, Instant updatedAt) {
    Place place = new Place();
    place.id = id; place.name = "Lugar " + id; place.status = PlaceStatus.PENDING; place.createdBy = author; place.category = new com.wherefood.domain.Category(); place.createdAt = updatedAt.minusSeconds(60); place.updatedAt = updatedAt;
    return place;
  }

  private static PlaceVisitReview review(PlaceVisit visit, User author, short overall, Short taste, Short price) {
    PlaceVisitReview review = new PlaceVisitReview();
    review.visit = visit; review.author = review.updatedBy = author; review.overall = overall; review.taste = taste; review.price = price; return review;
  }

  private static PlaceReviewSummary placeReview(Long placeId, String author, Short location, Short service) {
   PlaceReviewSummary review = mock(PlaceReviewSummary.class);
   when(review.getPlaceId()).thenReturn(placeId); when(review.getAuthor()).thenReturn(author); when(review.getLocation()).thenReturn(location); when(review.getHeating()).thenReturn(null); when(review.getBathrooms()).thenReturn(null); when(review.getExterior()).thenReturn(null); when(review.getSeating()).thenReturn(null); when(review.getService()).thenReturn(service); when(review.getAmbiance()).thenReturn(null); return review;
  }

  private static User user(Long id, String username) { User user = new User(); user.id = id; user.username = username; return user; }
  private static Api apiForVisitMutations(Places places, PlaceVisits visits, CoupleAuthorizationService authorization) {
    return new Api(null, null, null, places, visits, null, null, null, null, null, null, null, null,
            new PlaceVisitReviewService(null, visits, authorization),
            new PlaceVisitService(places, visits, authorization));
  }

  private static Api apiWithMedia(Places places, PlaceVisits visits, Items items, Photos itemPhotos,
          PlaceReviews placeReviews, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos,
          PlaceVisitReviews visitReviews, PlaceMediaService media) {
    return new Api(null, null, null, places, visits, items, itemPhotos, null, placeReviews,
            placePhotos, visitPhotos, visitReviews, null, null, null, null, null, null, null, media);
  }
}
