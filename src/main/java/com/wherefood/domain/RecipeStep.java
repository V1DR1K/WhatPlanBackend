package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "recipe_steps")
public class RecipeStep extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id", nullable = false) public Recipe recipe;
 @Column(nullable = false, length = 2000) public String instruction;
 @Column(nullable = false) public int position;
}
