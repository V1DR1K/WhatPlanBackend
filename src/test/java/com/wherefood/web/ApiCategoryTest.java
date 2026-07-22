package com.wherefood.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Category;
import com.wherefood.repo.Repositories.Categories;
import org.junit.jupiter.api.Test;

class ApiCategoryTest {
  @Test
  void setsTheCreationTimestampForNewCategories() {
    Categories categories = mock(Categories.class);
    when(categories.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

    new Api(null, categories, null, null, null, null, null, null, null, null, null, null, null)
      .addCategory(new CategoryRequest("Cafe", "cafe", "C", true));

    org.mockito.Mockito.verify(categories).save(org.mockito.ArgumentMatchers.argThat(category -> category.createdAt != null));
  }
}
