package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "home_recipe_photos")
public class HomeRecipePhoto {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id") public HomeRecipe recipe;
 @Column(columnDefinition = "text") public String imageBase64;
 @Column(columnDefinition = "text") public String thumbnailBase64;
 public int width;
 public int height;
 public Instant createdAt;
}
