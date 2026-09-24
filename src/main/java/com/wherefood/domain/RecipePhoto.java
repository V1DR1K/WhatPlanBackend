package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recipe_photos")
public class RecipePhoto extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id", nullable = false, unique = true) public Recipe recipe;
 @Column(nullable = false, columnDefinition = "text") public String imageBase64;
 @Column(nullable = false, columnDefinition = "text") public String thumbnailBase64;
 @Column(nullable = false) public int width;
 @Column(nullable = false) public int height;
 @Column(nullable = false) public Instant createdAt;
}
