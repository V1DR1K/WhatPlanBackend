package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="place_photos") public class PlacePhoto {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="place_id") public Place place;
 @Column(columnDefinition="text") public String imageBase64;
 @Column(columnDefinition="text") public String thumbnailBase64;
 public int width; public int height; public Instant createdAt;
}
