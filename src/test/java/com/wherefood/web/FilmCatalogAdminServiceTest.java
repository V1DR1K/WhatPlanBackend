package com.wherefood.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.FilmGenreOption;
import com.wherefood.domain.WatchPlatform;
import com.wherefood.repo.Repositories.FilmGenreOptions;
import com.wherefood.repo.Repositories.WatchPlatforms;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilmCatalogAdminServiceTest {
    @Mock WatchPlatforms platforms;
    @Mock FilmGenreOptions genres;

    private FilmCatalogAdminService service() {
        return new FilmCatalogAdminService(platforms, genres);
    }

    @Test
    void deletePlatform_deactivatesInsteadOfProbingPrivateFilms() {
        WatchPlatform platform = new WatchPlatform();
        platform.id = 3L;
        when(platforms.findById(3L)).thenReturn(Optional.of(platform));

        service().deletePlatform(3L);

        assertThat(platform.active).isFalse();
        verify(platforms).save(platform);
    }

    @Test
    void deleteGenre_deactivatesInsteadOfDeletingPrivateFilmLinks() {
        FilmGenreOption genre = new FilmGenreOption();
        genre.id = 8L;
        when(genres.findById(8L)).thenReturn(Optional.of(genre));

        service().deleteGenre(8L);

        assertThat(genre.active).isFalse();
        verify(genres).save(genre);
    }

    @Test
    void updateGenre_canReactivateExistingOption() {
        FilmGenreOption genre = new FilmGenreOption();
        genre.id = 8L;
        genre.active = false;
        when(genres.findById(8L)).thenReturn(Optional.of(genre));

        service().updateGenre(8L, new FilmGenreOptionRequest("Drama", "🎭", true));

        assertThat(genre.active).isTrue();
        verify(genres).save(genre);
    }
}
