package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "film_photos")
public class FilmPhoto {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "film_id", nullable = false, unique = true) public Film film;
 @Column(nullable = false, columnDefinition = "text") public String imageBase64;
 @Column(nullable = false, columnDefinition = "text") public String thumbnailBase64;
 @Column(nullable = false) public int width;
 @Column(nullable = false) public int height;
 @Column(nullable = false) public Instant createdAt;
}
