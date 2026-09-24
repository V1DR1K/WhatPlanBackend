package com.wherefood.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "home_recipe_ingredients")
public class HomeRecipeIngredient extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id") public HomeRecipe recipe;
 @Column(nullable = false) public String name;
 public BigDecimal quantity;
 @Column(nullable = false) public String unit;
 @Column(nullable = false) public int position;
}
