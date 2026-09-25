package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.Photos;
import com.wherefood.repo.Repositories.PlacePhotos;
import com.wherefood.repo.Repositories.PlaceVisitPhotos;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PlaceMediaServiceTest {
    private final Places places = mock(Places.class);
    private final PlaceVisits visits = mock(PlaceVisits.class);
    private final Items items = mock(Items.class);
    private final PlacePhotos placePhotos = mock(PlacePhotos.class);
    private final PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    private final Photos itemPhotos = mock(Photos.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final UUID requestCouple = UUID.randomUUID();
    private final PlaceMediaService service = new PlaceMediaService(places, visits, items, placePhotos,
            visitPhotos, itemPhotos, storage, new CoupleAuthorizationService(members));

    @BeforeEach
    void establishRequestCouple() {
        CoupleContext.set(requestCouple);
    }

    @AfterEach
    void clearRequestCouple() {
        CoupleContext.clear();
    }

    @Test
    void uploadItemPhoto_whenActorBelongsToAnotherCouple_returns404BeforeLoadingOrStoring() throws Exception {
        User actor = user(7L);
        when(members.findActiveCoupleIdByUserId(actor.id)).thenReturn(Optional.of(UUID.randomUUID()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.uploadItemPhoto(40L, null, actor));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(items, never()).findByIdAndCoupleId(40L, requestCouple);
        verify(storage, never()).store((com.wherefood.domain.Item) null, null);
    }

    private static User user(Long id) {
        User user = new User();
        user.id = id;
        user.role = Role.USER;
        return user;
    }
}
