package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "film_reviews")
public class FilmReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "film_id") public Film film;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "view_id") public FilmView view;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") public User author;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by") public User updatedBy;
 @Column(nullable = false) public short rating;
 public String comment;
 @Column(name = "favorite_character", length = 300) public String favoriteCharacter;
 @ElementCollection
 @CollectionTable(name = "film_review_metrics", joinColumns = @JoinColumn(name = "review_id"))
 @MapKeyColumn(name = "metric_key")
 @Column(name = "level", nullable = false)
 public Map<String, Short> metrics = new LinkedHashMap<>();
 public Instant createdAt;
 public Instant updatedAt;
}
