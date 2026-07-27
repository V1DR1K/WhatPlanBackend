package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.repo.Repositories.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhenDatesApiTest {
 @Test
 void includesAnnualMatchesAndExcludesFutureExperiences() {
  SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class);
  SpecialDate anniversary = new SpecialDate(); anniversary.id = 3L; anniversary.label = "Aniversario"; anniversary.date = LocalDate.of(2020, 2, 14); anniversary.recurrence = SpecialDateRecurrence.ANNUAL;
  Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
  PlaceVisit matching = new PlaceVisit(); matching.id = 12L; matching.place = place; matching.visitedOn = LocalDate.of(2026, 2, 14);
  PlaceVisit future = new PlaceVisit(); future.id = 13L; future.place = place; future.visitedOn = LocalDate.of(2026, 12, 14);
   PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
   when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findAll()).thenReturn(List.of(matching, future)); when(placePhotos.findByPlaceId(8L)).thenReturn(Optional.empty()); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(anyLong())).thenReturn(List.of());

   Slice<WhenDateEntryDto> result = api(specialDates, visits, placePhotos, visitPhotos).list("2026-02", null, null, 12);

  assertEquals(1, result.content().size()); assertEquals("La cena", result.content().getFirst().title()); assertEquals("Aniversario", result.content().getFirst().specialDates().getFirst().label());
  }

  @Test
  void exposesEveryPhotoFromTheMatchingVisit() {
   SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
   SpecialDate anniversary = new SpecialDate(); anniversary.id = 3L; anniversary.label = "Aniversario"; anniversary.date = LocalDate.of(2020, 2, 14); anniversary.recurrence = SpecialDateRecurrence.ANNUAL;
   Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
   PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = LocalDate.of(2026, 2, 14);
   PlaceVisitPhoto first = new PlaceVisitPhoto(); first.id = 24L; first.width = 1200; first.height = 800;
   PlaceVisitPhoto second = new PlaceVisitPhoto(); second.id = 25L; second.width = 800; second.height = 1200;
   when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findAll()).thenReturn(List.of(visit)); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(12L)).thenReturn(List.of(first, second));

   WhenDateEntryDto entry = api(specialDates, visits, placePhotos, visitPhotos).list("2026-02", null, null, 12).content().getFirst();

   assertEquals(2, entry.sourcePhotos().size()); assertEquals("/place-visit-photos/24", entry.sourcePhotos().getFirst().url()); assertEquals("/place-visit-photos/25?thumbnail=true", entry.sourcePhotos().get(1).thumbnailUrl());
  }

 private static WhenDatesApi api(SpecialDates specialDates, PlaceVisits visits, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos) {
   return new WhenDatesApi(specialDates, mock(SpecialDateOccurrences.class), mock(SpecialDateOccurrenceComments.class), mock(SpecialDateOccurrencePhotos.class), visits, mock(FilmViews.class), mock(Cookings.class), mock(WhyFunVisits.class), placePhotos, visitPhotos, mock(FilmPhotos.class), mock(RecipePhotos.class), mock(WhyFunVenuePhotos.class), mock(WhyFunVisitPhotos.class), mock(PhotoStorage.class));
 }
}
