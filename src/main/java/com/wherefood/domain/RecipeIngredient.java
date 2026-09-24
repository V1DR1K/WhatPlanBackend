package com.wherefood.domain;

import jakarta.persistence.*;
import java.math.*;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id", nullable = false) public Recipe recipe;
 @Column(nullable = false) public String name;
 public BigDecimal quantity;
 @Column(nullable = false) public String unit;
 @Column(nullable = false) public int position;
}
