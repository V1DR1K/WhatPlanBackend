package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "home_recipe_ingredients")
public class HomeRecipeIngredient {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id") public HomeRecipe recipe;
 @Column(nullable = false) public String name;
 @Column(nullable = false) public int grams;
 @Column(nullable = false) public int position;
}
