package com.wherefood.web;

import com.wherefood.domain.WhyFunCategory;
import com.wherefood.repo.Repositories.WhyFunCategories;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only management of the global activity taxonomy. */
@Service
@Transactional(readOnly = true)
public class WhyFunCategoryAdminService {
    private final WhyFunCategories categories;

    public WhyFunCategoryAdminService(WhyFunCategories categories) {
        this.categories = categories;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WhyFunCategory create(FunCategoryRequest request) {
        WhyFunCategory value = new WhyFunCategory();
        apply(value, request, null);
        value.createdAt = value.updatedAt = java.time.Instant.now();
        return categories.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WhyFunCategory update(Long id, FunCategoryRequest request) {
        WhyFunCategory value = find(id);
        Long currentParentId = value.parent == null ? null : value.parent.id;
        if (!java.util.Objects.equals(currentParentId, request.parentId()) && categories.existsByParentId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés cambiar la jerarquía de una categoría que tiene subcategorías");
        }
        apply(value, request, value);
        value.updatedAt = java.time.Instant.now();
        return categories.save(value);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        WhyFunCategory value = find(id);
        value.active = false;
        categories.save(value);
    }

    private WhyFunCategory find(Long id) {
        return categories.findDetailedById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Categoría no encontrada"));
    }

    private void apply(WhyFunCategory value, FunCategoryRequest request, WhyFunCategory current) {
        WhyFunCategory parent = request.parentId() == null ? null : find(request.parentId());
        if (parent != null && parent.parent != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las subcategorías solo pueden tener una categoría principal");
        }
        if (parent != null && parent.id.equals(value.id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una categoría no puede ser su propia subcategoría");
        }
        String slug = slugFor(request.name());
        if (slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe incluir letras o números");
        }
        Optional<WhyFunCategory> duplicate = parent == null
                ? categories.findByParentIsNullAndSlug(slug)
                : categories.findByParentIdAndSlug(parent.id, slug);
        if (duplicate.isPresent() && (current == null || !duplicate.get().id.equals(current.id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre");
        }
        value.parent = parent;
        value.name = request.name().trim();
        value.slug = slug;
        value.icon = request.icon().trim();
        value.active = request.active();
    }

    private static String slugFor(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
