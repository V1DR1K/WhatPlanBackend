package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "film_views")
public class FilmView {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "film_id") public Film film;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") public User createdBy;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by") public User updatedBy;
 @Column(nullable = false) public LocalDate watchedOn;
 public Instant createdAt;
}
