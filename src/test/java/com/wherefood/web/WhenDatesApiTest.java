package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateOccurrence;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.repo.Repositories.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhenDatesApiTest {
 @Test
 void pagesSummariesInDatabaseAndPreservesStableOffsetCursor() {
  SpecialDates specialDates = mock(SpecialDates.class);
  WhenDateSummaryProjection first = summary(3L, "Aniversario", "ANNUAL", LocalDate.of(2026, 2, 14), 2L, null);
  WhenDateSummaryProjection second = summary(4L, "Cumplemes", "MONTHLY", LocalDate.of(2026, 1, 9), 0L, "/when-dates/photos/91?thumbnail=true");
  WhenDateSummaryProjection third = summary(5L, "Next", "ONCE", LocalDate.of(2025, 12, 1), 1L, null);
  when(specialDates.findSummaryPageByCoupleId(isNull(), isNull(), any(), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(12L)))
          .thenReturn(List.of(first, second, third));

  Slice<WhenDateOccurrenceSummaryDto> result = api(specialDates).list(null, 12L, 2);

  assertEquals(2, result.content().size());
  assertEquals("Aniversario", result.content().getFirst().specialDate().label());
  assertEquals(2, result.content().getFirst().experienceCount());
  assertEquals(SpecialDateRecurrence.MONTHLY, result.content().get(1).specialDate().recurrence());
  assertEquals(0, result.content().get(1).experienceCount());
  assertEquals("/when-dates/photos/91?thumbnail=true", result.content().get(1).imageUrl());
  assertEquals(14L, result.nextCursor());
  verify(specialDates).findSummaryPageByCoupleId(isNull(), isNull(), any(), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(12L));
 }

 @Test
 void filtersSummaryQueryByRequestedSpecialDate() {
  SpecialDates specialDates = mock(SpecialDates.class);
  WhenDateSummaryProjection only = summary(6L, "Fecha única", "ONCE", LocalDate.of(2025, 7, 28), 1L, null);
  when(specialDates.findSummaryPageByCoupleId(isNull(), org.mockito.ArgumentMatchers.eq(6L), any(), org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.eq(0L)))
          .thenReturn(List.of(only));

  Slice<WhenDateOccurrenceSummaryDto> result = api(specialDates).list(6L, null, 12);

  assertEquals("Fecha única", result.content().getFirst().specialDate().label());
  assertEquals(null, result.nextCursor());
 }

 @Test
 void exposesEveryPhotoFromTheMatchingVisitForOnlyTheRequestedDate() {
  SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class);
  PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
  SpecialDate anniversary = new SpecialDate(); anniversary.id = 3L; anniversary.label = "Aniversario"; anniversary.date = LocalDate.of(2020, 2, 14); anniversary.recurrence = SpecialDateRecurrence.ANNUAL;
  Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
  PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = LocalDate.of(2026, 2, 14);
  PlaceVisitPhoto first = new PlaceVisitPhoto(); first.id = 24L; first.width = 1200; first.height = 800;
  PlaceVisitPhoto second = new PlaceVisitPhoto(); second.id = 25L; second.width = 800; second.height = 1200;
  when(specialDates.findByIdAndCoupleId(3L, null)).thenReturn(Optional.of(anniversary));
  when(visits.findByCoupleIdAndVisitedOnOrderByVisitedOnDescIdDesc(null, visit.visitedOn)).thenReturn(List.of(visit));
  when(placePhotos.findByPlaceIdAndCoupleId(8L, null)).thenReturn(Optional.empty());
  when(visitPhotos.findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(12L, null)).thenReturn(List.of(first, second));

  WhenDateEntryDto entry = api(specialDates, visits, placePhotos, visitPhotos).occurrence(3L, visit.visitedOn).entries().getFirst();

  assertEquals(2, entry.sourcePhotos().size());
  assertEquals("/place-visit-photos/24", entry.sourcePhotos().getFirst().url());
  assertEquals("/place-visit-photos/25?thumbnail=true", entry.sourcePhotos().get(1).thumbnailUrl());
  verify(visits).findByCoupleIdAndVisitedOnOrderByVisitedOnDescIdDesc(null, visit.visitedOn);
 }

 private static WhenDateSummaryProjection summary(Long id, String label, String recurrence, LocalDate date, Long count, String imageUrl) {
  WhenDateSummaryProjection projection = mock(WhenDateSummaryProjection.class);
  when(projection.getSpecialDateId()).thenReturn(id);
  when(projection.getLabel()).thenReturn(label);
  when(projection.getRecurrence()).thenReturn(recurrence);
  when(projection.getOccurredOn()).thenReturn(date);
  when(projection.getExperienceCount()).thenReturn(count);
  when(projection.getImageUrl()).thenReturn(imageUrl);
  return projection;
 }

 private static WhenDatesApi api(SpecialDates specialDates) {
  return api(specialDates, mock(PlaceVisits.class), mock(PlacePhotos.class), mock(PlaceVisitPhotos.class));
 }

 private static WhenDatesApi api(SpecialDates specialDates, PlaceVisits visits, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos) {
  return new WhenDatesApi(specialDates, mock(SpecialDateOccurrences.class), mock(SpecialDateOccurrenceComments.class), mock(SpecialDateOccurrencePhotos.class), visits, mock(FilmViews.class), mock(Cookings.class), mock(WhyFunVisits.class), placePhotos, visitPhotos, mock(FilmPhotos.class), mock(RecipePhotos.class), mock(WhyFunVenuePhotos.class), mock(WhyFunVisitPhotos.class), mock(PhotoStorage.class), mock(CoupleMembers.class));
 }
}
