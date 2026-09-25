package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Film;
import com.wherefood.domain.FilmView;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.FilmViews;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FilmViewServiceTest {
    private final Films films = mock(Films.class);
    private final FilmViews views = mock(FilmViews.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final FilmViewService service = new FilmViewService(films, views,
            new CoupleAuthorizationService(members));
    private final UUID coupleId = UUID.randomUUID();
    private final LocalDate today = RosarioClock.today();

    @BeforeEach
    void establishActiveCouple() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void create_updatesFilmSummaryAtomicallyWithTheView() {
        User owner = user(5L, Role.USER);
        Film film = new Film();
        film.id = 42L;
        when(films.findDetailedByIdAndCoupleId(42L, coupleId)).thenReturn(Optional.of(film));
        when(views.findByFilmIdAndWatchedOnAndCoupleId(42L, today, coupleId))
                .thenReturn(Optional.empty());
        when(views.save(any(FilmView.class))).thenAnswer(call -> {
            FilmView view = call.getArgument(0);
            view.id = 90L;
            return view;
        });
        when(views.findByFilmIdAndCoupleIdOrderByWatchedOnDescIdDesc(42L, coupleId)).thenAnswer(call -> {
            FilmView view = new FilmView();
            view.watchedOn = today;
            return List.of(view);
        });

        FilmView saved = service.create(42L,
                new FilmViewRequest(today), owner);

        assertEquals(90L, saved.id);
        assertEquals(1, film.watchedCount);
        assertEquals(today, film.lastWatchedOn);
        verify(films).save(film);
    }

    @Test
    void create_withoutActiveMembership_returns404WithoutWriting() {
        User owner = user(5L, Role.USER);
        when(members.findActiveCoupleIdByUserId(owner.id)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(42L, new FilmViewRequest(today), owner))
                .getStatusCode().value());
        verify(films, never()).save(any(Film.class));
        verify(views, never()).save(any(FilmView.class));
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
