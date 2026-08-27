package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.domain.SpecialDateOccurrence;
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
   PlaceVisit matchingAgain = new PlaceVisit(); matchingAgain.id = 14L; matchingAgain.place = place; matchingAgain.visitedOn = LocalDate.of(2026, 2, 14);
   PlaceVisit future = new PlaceVisit(); future.id = 13L; future.place = place; future.visitedOn = LocalDate.of(2026, 12, 14);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
     when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of(matching, matchingAgain, future)); when(placePhotos.findByPlaceId(8L)).thenReturn(Optional.empty()); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(anyLong())).thenReturn(List.of());

    Slice<WhenDateOccurrenceSummaryDto> result = api(specialDates, visits, placePhotos, visitPhotos).list(null, null, 12);

   assertEquals(1, result.content().size()); assertEquals("Aniversario", result.content().getFirst().specialDate().label()); assertEquals(2, result.content().getFirst().experienceCount());
  }

  @Test
  void exposesEveryPhotoFromTheMatchingVisit() {
   SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
   SpecialDate anniversary = new SpecialDate(); anniversary.id = 3L; anniversary.label = "Aniversario"; anniversary.date = LocalDate.of(2020, 2, 14); anniversary.recurrence = SpecialDateRecurrence.ANNUAL;
   Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
   PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = LocalDate.of(2026, 2, 14);
   PlaceVisitPhoto first = new PlaceVisitPhoto(); first.id = 24L; first.width = 1200; first.height = 800;
   PlaceVisitPhoto second = new PlaceVisitPhoto(); second.id = 25L; second.width = 800; second.height = 1200;
    when(specialDates.findById(3L)).thenReturn(Optional.of(anniversary)); when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of(visit)); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(12L)).thenReturn(List.of(first, second));

    WhenDateEntryDto entry = api(specialDates, visits, placePhotos, visitPhotos).occurrence(3L, LocalDate.of(2026, 2, 14)).entries().getFirst();

   assertEquals(2, entry.sourcePhotos().size()); assertEquals("/place-visit-photos/24", entry.sourcePhotos().getFirst().url()); assertEquals("/place-visit-photos/25?thumbnail=true", entry.sourcePhotos().get(1).thumbnailUrl());
  }

  @Test
  void prefersTheOccurrenceCoverForTheMatchingSpecialDate() {
   SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); SpecialDateOccurrences occurrences = mock(SpecialDateOccurrences.class);
   SpecialDate anniversary = new SpecialDate(); anniversary.id = 3L; anniversary.label = "Aniversario"; anniversary.date = LocalDate.of(2020, 2, 14); anniversary.recurrence = SpecialDateRecurrence.ANNUAL;
   Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
   PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = LocalDate.of(2026, 2, 14);
   SpecialDateOccurrence occurrence = new SpecialDateOccurrence(); occurrence.specialDate = anniversary; occurrence.occurredOn = visit.visitedOn; occurrence.coverPhotoId = 91L;
     when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(anniversary)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of(visit)); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(12L)).thenReturn(List.of()); when(occurrences.findByOccurredOnLessThanEqualOrderByOccurredOnDescIdDesc(any())).thenReturn(List.of(occurrence));

    WhenDateOccurrenceSummaryDto entry = api(specialDates, visits, placePhotos, visitPhotos, occurrences).list(null, null, 12).content().getFirst();

    assertEquals("/when-dates/photos/91", entry.imageUrl());
   }

   @Test
   void includesStandaloneOccurrencesWithoutMatchingExperiences() {
    SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); SpecialDateOccurrences occurrences = mock(SpecialDateOccurrences.class);
    SpecialDate birthday = new SpecialDate(); birthday.id = 5L; birthday.label = "Cumplemes"; birthday.date = LocalDate.of(2020, 6, 9); birthday.recurrence = SpecialDateRecurrence.MONTHLY;
    SpecialDateOccurrence occurrence = new SpecialDateOccurrence(); occurrence.specialDate = birthday; occurrence.occurredOn = LocalDate.of(2026, 6, 9);
     when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(birthday)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of()); when(occurrences.findByOccurredOnLessThanEqualOrderByOccurredOnDescIdDesc(any())).thenReturn(List.of(occurrence));

    WhenDateOccurrenceSummaryDto result = api(specialDates, visits, placePhotos, visitPhotos, occurrences).list(null, null, 12).content().getFirst();

   assertEquals("Cumplemes", result.specialDate().label()); assertEquals(0, result.experienceCount()); assertEquals(LocalDate.of(2026, 6, 9), result.occurredOn());
   }

   @Test
   void treatsLegacyDatesWithoutRecurrenceAsOneOffs() {
    SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    SpecialDate legacy = new SpecialDate(); legacy.id = 6L; legacy.label = "Fecha histórica"; legacy.date = LocalDate.of(2025, 7, 28);
    Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
    PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = legacy.date;
     when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(legacy)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of(visit)); when(placePhotos.findByPlaceId(8L)).thenReturn(Optional.empty()); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(12L)).thenReturn(List.of());

    WhenDateOccurrenceSummaryDto result = api(specialDates, visits, placePhotos, visitPhotos).list(null, null, 12).content().getFirst();

   assertEquals(SpecialDateRecurrence.ONCE, result.specialDate().recurrence());
   }

   @Test
   void ignoresLegacyDatesWithoutDatesWhenMatchingExperiences() {
    SpecialDates specialDates = mock(SpecialDates.class); PlaceVisits visits = mock(PlaceVisits.class); PlacePhotos placePhotos = mock(PlacePhotos.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    SpecialDate invalid = new SpecialDate(); invalid.id = 6L; invalid.label = "Fecha incompleta"; invalid.recurrence = SpecialDateRecurrence.ONCE;
    SpecialDate valid = new SpecialDate(); valid.id = 7L; valid.label = "Fecha válida"; valid.date = LocalDate.of(2025, 7, 28); valid.recurrence = SpecialDateRecurrence.ONCE;
    Place place = new Place(); place.id = 8L; place.name = "La cena"; place.address = "Rosario";
    PlaceVisit visit = new PlaceVisit(); visit.id = 12L; visit.place = place; visit.visitedOn = valid.date;
     when(specialDates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(invalid, valid)); when(visits.findByVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(any())).thenReturn(List.of(visit)); when(placePhotos.findByPlaceId(8L)).thenReturn(Optional.empty()); when(visitPhotos.findByVisitIdOrderByPositionAscIdAsc(12L)).thenReturn(List.of());

    WhenDateOccurrenceSummaryDto result = api(specialDates, visits, placePhotos, visitPhotos).list(null, null, 12).content().getFirst();

    assertEquals("Fecha válida", result.specialDate().label());
   }

  private static WhenDatesApi api(SpecialDates specialDates, PlaceVisits visits, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos) {
   return api(specialDates, visits, placePhotos, visitPhotos, mock(SpecialDateOccurrences.class));
  }

  private static WhenDatesApi api(SpecialDates specialDates, PlaceVisits visits, PlacePhotos placePhotos, PlaceVisitPhotos visitPhotos, SpecialDateOccurrences occurrences) {
    return new WhenDatesApi(specialDates, occurrences, mock(SpecialDateOccurrenceComments.class), mock(SpecialDateOccurrencePhotos.class), visits, mock(FilmViews.class), mock(Cookings.class), mock(WhyFunVisits.class), placePhotos, visitPhotos, mock(FilmPhotos.class), mock(RecipePhotos.class), mock(WhyFunVenuePhotos.class), mock(WhyFunVisitPhotos.class), mock(PhotoStorage.class));
  }
}
