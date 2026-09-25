package com.wherefood.web;

import com.wherefood.domain.Category;
import com.wherefood.domain.HighlightTag;
import com.wherefood.repo.Repositories.Categories;
import com.wherefood.repo.Repositories.HighlightTags;
import com.wherefood.repo.Repositories.Places;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only management of global place categories and highlight tags. */
@Service
@Transactional(readOnly = true)
public class PlaceCatalogAdminService {
    private final Categories categories;
    private final HighlightTags tags;
    private final Places places;

    public PlaceCatalogAdminService(Categories categories, HighlightTags tags, Places places) {
        this.categories = categories;
        this.tags = tags;
        this.places = places;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Category createCategory(CategoryRequest request) {
        Category value = new Category();
        apply(value, request);
        value.createdAt = Instant.now();
        return categories.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Category updateCategory(Long id, CategoryRequest request) {
        Category value = categories.findById(id).orElseThrow(() -> notFound("Categoría"));
        apply(value, request);
        return categories.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(Long id) {
        Category value = categories.findById(id).orElseThrow(() -> notFound("Categoría"));
        if (places.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés borrar un rubro que tiene lugares asociados");
        }
        categories.delete(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public HighlightTag createTag(HighlightTagRequest request) {
        HighlightTag value = new HighlightTag();
        apply(value, request);
        return tags.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public HighlightTag updateTag(Long id, HighlightTagRequest request) {
        HighlightTag value = tags.findById(id).orElseThrow(() -> notFound("Etiqueta"));
        apply(value, request);
        return tags.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteTag(Long id) {
        HighlightTag value = tags.findById(id).orElseThrow(() -> notFound("Etiqueta"));
        if (places.existsByHighlightTagsId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés borrar una etiqueta asignada a lugares");
        }
        tags.delete(value);
    }

    private static void apply(Category value, CategoryRequest request) {
        value.name = request.name();
        value.slug = request.slug();
        value.icon = request.icon();
        value.active = request.active();
    }

    private static void apply(HighlightTag value, HighlightTagRequest request) {
        value.name = request.name().trim();
        value.emoji = request.emoji().trim();
    }

    private static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " no encontrado");
    }
}
