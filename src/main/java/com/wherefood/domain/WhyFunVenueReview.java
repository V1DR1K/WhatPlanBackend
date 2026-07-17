package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "why_fun_venue_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "author_id"}))
public class WhyFunVenueReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venue_id", nullable = false) public WhyFunVenue venue;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) public User author;
 @Column(nullable = false) public short rating;
 @Column(length = 1000) public String comment;
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
