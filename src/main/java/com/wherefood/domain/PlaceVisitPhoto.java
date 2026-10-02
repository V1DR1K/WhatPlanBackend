package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "place_visit_photos")
public class PlaceVisitPhoto extends CoupleScopedEntity {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "visit_id", nullable = false) public PlaceVisit visit;
 @Column(columnDefinition = "text", nullable = false) public String imageBase64;
 @Column(columnDefinition = "text", nullable = false) public String thumbnailBase64;
 public int width;
 public int height;
 @Column(nullable = false) public int position;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 public Instant createdAt;
}
