package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "cooking_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"cooking_id", "author_id"}))
public class CookingReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "cooking_id", nullable = false) public Cooking cooking;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) public User author;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(nullable = false) public short rating;
 @Column(nullable = false) public short complexity;
 @Column(nullable = false) public short taste;
 @Column(length = 1000) public String comment;
 public Instant createdAt;
 public Instant updatedAt;
}
