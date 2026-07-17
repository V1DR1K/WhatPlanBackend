package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "why_fun_venue_photos")
public class WhyFunVenuePhoto {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venue_id", nullable = false) public WhyFunVenue venue;
 @Column(name = "image_base64", columnDefinition = "text", nullable = false) public String imageBase64;
 @Column(name = "thumbnail_base64", columnDefinition = "text", nullable = false) public String thumbnailBase64;
 @Column(nullable = false) public int width;
 @Column(nullable = false) public int height;
 @Column(nullable = false) public Instant createdAt;
}
