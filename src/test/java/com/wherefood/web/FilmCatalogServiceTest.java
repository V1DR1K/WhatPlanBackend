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
import com.wherefood.domain.FilmGenreOption;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.domain.WatchPlatform;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.FilmGenreOptions;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.WatchPlatforms;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FilmCatalogServiceTest {
    private final Films films = mock(Films.class);
    private final FilmGenreOptions genres = mock(FilmGenreOptions.class);
    private final WatchPlatforms platforms = mock(WatchPlatforms.class);
    private final TmdbClient tmdb = mock(TmdbClient.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final FilmCatalogService service = new FilmCatalogService(films, genres, platforms, tmdb,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishCoupleContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(7L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void create_whenActiveMember_savesNewFilm() {
        User member = user(7L, Role.USER);
        when(genres.findAllByNameIn(any())).thenReturn(List.of());
        when(films.save(any(Film.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Film created = service.create(manualFilm("Arrival"), member);

        assertEquals("Arrival", created.title);
        assertEquals(member, created.createdBy);
        verify(films).save(created);
    }

    @Test
    void update_whenFilmBelongsToAnotherCouple_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        when(films.findDetailedByIdAndCoupleId(55L, coupleId)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(55L, manualFilm("Edited"), member)).getStatusCode().value());
        verify(films, never()).save(any(Film.class));
    }

    @Test
    void create_whenAdminAttemptsPrivateWrite_returns404() {
        User admin = user(7L, Role.ADMIN);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(manualFilm("Forbidden"), admin)).getStatusCode().value());
        verify(films, never()).save(any(Film.class));
    }

    @Test
    void create_whenSelectingInactiveGenre_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        FilmGenreOption inactive = genre("Drama");
        inactive.active = false;
        when(genres.findAllByNameIn(any())).thenReturn(List.of(inactive));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(manualFilm("Arrival", List.of("Drama")), member)).getStatusCode().value());
        verify(films, never()).save(any(Film.class));
    }

    @Test
    void update_whenKeepingPreviouslyAssignedInactiveGenre_preservesIt() {
        User member = user(7L, Role.USER);
        Film film = new Film();
        film.id = 55L;
        FilmGenreOption inactive = genre("Drama");
        inactive.active = false;
        film.genres.add(inactive);
        when(films.findDetailedByIdAndCoupleId(55L, coupleId)).thenReturn(Optional.of(film));
        when(genres.findAllByNameIn(any())).thenReturn(List.of(inactive));
        when(films.save(film)).thenReturn(film);

        Film updated = service.update(55L, manualFilm("Arrival", List.of("Drama")), member);

        assertEquals(List.of(inactive), List.copyOf(updated.genres));
        verify(films).save(film);
    }

    @Test
    void create_whenSelectingInactivePlatform_returns404WithoutSaving() {
        User member = user(7L, Role.USER);
        WatchPlatform inactive = new WatchPlatform();
        inactive.id = 12L;
        inactive.active = false;
        when(platforms.findById(12L)).thenReturn(Optional.of(inactive));
        FilmRequest request = new FilmRequest(null, "Arrival", null, null, null, null, null, List.of(), 12L);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.create(request, member)).getStatusCode().value());
        verify(films, never()).save(any(Film.class));
    }

    private static FilmRequest manualFilm(String title) {
        return new FilmRequest(null, title, null, null, null, null, null, List.of(), null);
    }

    private static FilmRequest manualFilm(String title, List<String> genres) {
        return new FilmRequest(null, title, null, null, null, null, null, genres, null);
    }

    private static FilmGenreOption genre(String name) {
        FilmGenreOption value = new FilmGenreOption();
        value.name = name;
        return value;
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
