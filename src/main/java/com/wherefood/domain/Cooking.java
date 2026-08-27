package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "cookings")
public class Cooking {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id", nullable = false) public Recipe recipe;
 @Enumerated(EnumType.STRING) @Column(nullable = false) public Home home;
 @Column(nullable = false) public int servings;
 @Column(name = "cooked_on", nullable = false) public LocalDate cookedOn;
 @Enumerated(EnumType.STRING) @Column(name = "meal_type", nullable = false) public MealType mealType;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 public Instant createdAt;
 public Instant updatedAt;
}
