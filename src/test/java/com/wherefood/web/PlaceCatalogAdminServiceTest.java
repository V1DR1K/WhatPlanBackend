package com.wherefood.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Category;
import com.wherefood.domain.HighlightTag;
import com.wherefood.repo.Repositories.Categories;
import com.wherefood.repo.Repositories.HighlightTags;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceCatalogAdminServiceTest {
    @Mock Categories categories;
    @Mock HighlightTags tags;

    private PlaceCatalogAdminService service() {
        return new PlaceCatalogAdminService(categories, tags);
    }

    @Test
    void deleteCategory_deactivatesWithoutCheckingPrivatePlaceUsage() {
        Category category = new Category();
        category.id = 4L;
        when(categories.findById(4L)).thenReturn(Optional.of(category));

        service().deleteCategory(4L);

        assertThat(category.active).isFalse();
        verify(categories).save(category);
    }

    @Test
    void deleteTag_deactivatesWithoutCheckingPrivatePlaceUsage() {
        HighlightTag tag = new HighlightTag();
        tag.id = 7L;
        when(tags.findById(7L)).thenReturn(Optional.of(tag));

        service().deleteTag(7L);

        assertThat(tag.active).isFalse();
        verify(tags).save(tag);
    }

    @Test
    void updateTag_canReactivateAnExistingGlobalOption() {
        HighlightTag tag = new HighlightTag();
        tag.id = 7L;
        tag.active = false;
        when(tags.findById(7L)).thenReturn(Optional.of(tag));

        service().updateTag(7L, new HighlightTagRequest("Sin TACC", "🌾", true));

        assertThat(tag.active).isTrue();
        verify(tags).save(tag);
    }
}
