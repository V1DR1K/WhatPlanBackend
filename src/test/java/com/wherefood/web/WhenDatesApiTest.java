package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
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
  when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findAll()).thenReturn(List.of(matching, future)); when(placePhotos.findByPlaceId(8L)).thenReturn(Optional.empty());

  Slice<WhenDateEntryDto> result = api(specialDates, visits, placePhotos).list("2026-02", null, null, 12);

  assertEquals(1, result.content().size()); assertEquals("La cena", result.content().getFirst().title()); assertEquals("Aniversario", result.content().getFirst().specialDates().getFirst().label());
 }

 private static WhenDatesApi api(SpecialDates specialDates, PlaceVisits visits, PlacePhotos placePhotos) {
  return new WhenDatesApi(specialDates, mock(SpecialDateOccurrences.class), mock(SpecialDateOccurrenceComments.class), mock(SpecialDateOccurrencePhotos.class), visits, mock(FilmViews.class), mock(Cookings.class), mock(WhyFunVisits.class), placePhotos, mock(FilmPhotos.class), mock(RecipePhotos.class), mock(WhyFunVenuePhotos.class), mock(PhotoStorage.class));
 }
}
