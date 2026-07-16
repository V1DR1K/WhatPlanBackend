package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "film_genre_options")
public class FilmGenreOption {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Column(nullable = false, unique = true, length = 80) public String name;
 @Column(nullable = false, length = 20) public String emoji;
 @Column(nullable = false) public Instant createdAt;
}
