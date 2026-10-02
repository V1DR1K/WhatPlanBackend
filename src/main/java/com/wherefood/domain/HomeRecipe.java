package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "home_recipes")
public class HomeRecipe extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") public User author;
 @Enumerated(EnumType.STRING) @Column(nullable = false) public Home home;
 @Column(nullable = false) public String name;
 @Column(nullable = false) public int servings;
 public String recipeUrl;
 @Column(nullable = false) public LocalDate preparedOn;
 @Enumerated(EnumType.STRING) @Column(nullable = false) public MealType mealType;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "repeated_from_id") public HomeRecipe repeatedFrom;
 @OneToMany(mappedBy = "repeatedFrom") public List<HomeRecipe> repetitions = new ArrayList<>();
 @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true) @OrderColumn(name = "position") public List<HomeRecipeIngredient> ingredients = new ArrayList<>();
 @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true) @OrderColumn(name = "position") public List<HomeRecipeStep> steps = new ArrayList<>();
 public Instant createdAt;
 public Instant updatedAt;
}
