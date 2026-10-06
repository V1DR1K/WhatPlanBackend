package com.wherefood.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Application input for a reusable recipe definition. */
public record RecipeInput(String name, String sourceUrl, List<Ingredient> ingredients,
        List<Step> steps, Long zoneId, UUID stageId) {
    public record Ingredient(String name, BigDecimal quantity, String unit) {}

    public record Step(String instruction) {}
}
