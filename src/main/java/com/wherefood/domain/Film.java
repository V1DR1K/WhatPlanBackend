package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "films")
public class Film {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @Column(name = "tmdb_id", unique = true) public Long tmdbId;
 @Column(nullable = false) public String title;
 public String originalTitle;
 @Column(length = 3000) public String synopsis;
 public LocalDate releaseDate;
 public String posterPath;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "platform_id") public WatchPlatform platform;
 @Column(nullable = false) public int watchedCount;
 public LocalDate lastWatchedOn;
 @ManyToMany @JoinTable(name = "film_genres", joinColumns = @JoinColumn(name = "film_id"), inverseJoinColumns = @JoinColumn(name = "genre_id")) public Set<FilmGenreOption> genres = new LinkedHashSet<>();
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") public User createdBy;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by") public User updatedBy;
 public Instant createdAt;
 public Instant updatedAt;
}
