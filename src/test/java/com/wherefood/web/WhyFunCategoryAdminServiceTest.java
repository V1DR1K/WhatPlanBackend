package com.wherefood.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.WhyFunCategory;
import com.wherefood.repo.Repositories.WhyFunCategories;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhyFunCategoryAdminServiceTest {
    @Mock WhyFunCategories categories;

    @Test
    void delete_deactivatesWithoutProbingPrivatePlansOrActivities() {
        WhyFunCategory category = new WhyFunCategory();
        category.id = 3L;
        when(categories.findDetailedById(3L)).thenReturn(Optional.of(category));

        new WhyFunCategoryAdminService(categories).delete(3L);

        assertThat(category.active).isFalse();
        verify(categories).save(category);
    }
}
