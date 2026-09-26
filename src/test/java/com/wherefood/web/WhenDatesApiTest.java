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
import com.wherefood.domain.Film;
import com.wherefood.domain.FilmPhoto;
import com.wherefood.domain.FilmView;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateOccurrence;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.repo.Repositories.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
 void rejectsSummaryCursorBeyondMaximumOffsetBeforeQueryingDatabase() {
  SpecialDates specialDates = mock(SpecialDates.class);

  org.junit.jupiter.api.Assertions.assertEquals(400,
          org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                  () -> api(specialDates).list(null, 10_001L, 12)).getStatusCode().value());

  org.mockito.Mockito.verifyNoInteractions(specialDates);
 }

 @Test
 void stopsSummaryPaginationAtMaximumOffset() {
  SpecialDates specialDates = mock(SpecialDates.class);
  WhenDateSummaryProjection first = summary(7L, "Última página", "ONCE", LocalDate.of(2025, 1, 1), 0L, null);
  WhenDateSummaryProjection extra = summary(8L, "Siguiente", "ONCE", LocalDate.of(2024, 1, 1), 0L, null);
  when(specialDates.findSummaryPageByCoupleId(isNull(), isNull(), any(), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(10_000L)))
          .thenReturn(List.of(first, extra));

  Slice<WhenDateOccurrenceSummaryDto> result = api(specialDates).list(null, 10_000L, 1);

  assertEquals(1, result.content().size());
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

 @Test
 void versionsMutableFilmPhotoUrlsInTimelineAndSourcePhotos() {
  SpecialDates specialDates = mock(SpecialDates.class);
  SpecialDate specialDate = new SpecialDate(); specialDate.id = 3L; specialDate.label = "Aniversario"; specialDate.date = LocalDate.of(2020, 2, 14); specialDate.recurrence = SpecialDateRecurrence.ANNUAL;
  LocalDate watchedOn = LocalDate.of(2026, 2, 14);
  Film film = new Film(); film.id = 20L; film.title = "Película privada";
  FilmView view = new FilmView(); view.id = 21L; view.film = film; view.watchedOn = watchedOn;
  FilmPhoto photo = new FilmPhoto(); photo.id = 55L; photo.width = 1200; photo.height = 1800;
  FilmViews filmViews = mock(FilmViews.class); FilmPhotos filmPhotos = mock(FilmPhotos.class);
  SpecialDateOccurrences occurrences = mock(SpecialDateOccurrences.class);
  when(specialDates.findByIdAndCoupleId(3L, null)).thenReturn(Optional.of(specialDate));
  when(occurrences.findBySpecialDateIdAndOccurredOnAndCoupleId(3L, watchedOn, null)).thenReturn(Optional.empty());
  when(filmViews.findByCoupleIdAndWatchedOnOrderByWatchedOnDescIdDesc(null, watchedOn)).thenReturn(List.of(view));
  when(filmPhotos.findByFilmIdAndCoupleId(20L, null)).thenReturn(Optional.of(photo));
  WhenDatesApi api = new WhenDatesApi(specialDates, occurrences, mock(SpecialDateOccurrenceComments.class), mock(SpecialDateOccurrencePhotos.class), mock(PlaceVisits.class), filmViews, mock(Cookings.class), mock(WhyFunVisits.class), mock(PlacePhotos.class), mock(PlaceVisitPhotos.class), filmPhotos, mock(RecipePhotos.class), mock(WhyFunVenuePhotos.class), mock(WhyFunVisitPhotos.class), mock(PhotoStorage.class), mock(CoupleMembers.class));

  WhenDateEntryDto entry = api.occurrence(3L, watchedOn).entries().getFirst();

  assertEquals("/films/20/photo?thumbnail=true&v=55", entry.imageUrl());
  assertEquals("/films/20/photo?v=55", entry.sourcePhotos().getFirst().url());
  assertEquals("/films/20/photo?thumbnail=true&v=55", entry.sourcePhotos().getFirst().thumbnailUrl());
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
