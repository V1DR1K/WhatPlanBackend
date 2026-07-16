package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "film_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"film_id", "author_id"}))
public class FilmReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "film_id") public Film film;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") public User author;
 @Column(nullable = false) public short rating;
 public String comment;
 public LocalDate watchedOn;
 public Instant createdAt;
 public Instant updatedAt;
}
