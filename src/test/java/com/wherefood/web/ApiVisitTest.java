package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceStatus;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiVisitTest {
  @Test
  void returnsThePlaceToPendingAfterDeletingItsLastVisit() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    User tomas = new User();
    tomas.id = 7L;
    Place place = new Place();
    place.id = 4L;
    place.status = PlaceStatus.REVIEWED;
    PlaceVisit visit = new PlaceVisit();
    visit.id = 9L;
    visit.place = place;
    visit.createdBy = tomas;
    when(visits.findDetailedById(9L)).thenReturn(Optional.of(visit));
    when(visits.existsByPlaceId(4L)).thenReturn(false);

    new Api(null, null, null, places, visits, null, null, null, null, null, null, null, null).deleteVisit(9L, tomas);

    assertEquals(PlaceStatus.PENDING, place.status);
    verify(visits).delete(visit);
    verify(places).save(place);
  }
}
