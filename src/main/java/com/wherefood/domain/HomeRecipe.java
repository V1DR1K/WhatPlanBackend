package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "home_recipes")
public class HomeRecipe {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") public User author;
 @Enumerated(EnumType.STRING) @Column(nullable = false) public Home home;
 @Column(nullable = false) public String name;
 public String recipeUrl;
 @Column(nullable = false) public LocalDate preparedOn;
 @Enumerated(EnumType.STRING) @Column(nullable = false) public MealType mealType;
 @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position asc") public List<HomeRecipeIngredient> ingredients = new ArrayList<>();
 public Instant createdAt;
 public Instant updatedAt;
}
