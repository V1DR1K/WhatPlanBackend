package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "home_recipe_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "author_id"}))
public class HomeRecipeReview extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id") public HomeRecipe recipe;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") public User author;
 @Column(nullable = false) public short rating;
 public String comment;
 public Instant createdAt;
 public Instant updatedAt;
}
