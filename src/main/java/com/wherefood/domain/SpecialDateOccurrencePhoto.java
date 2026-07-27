package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "special_date_occurrence_photos", uniqueConstraints = @UniqueConstraint(columnNames = {"occurrence_id", "position"}))
public class SpecialDateOccurrencePhoto {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "occurrence_id", nullable = false) public SpecialDateOccurrence occurrence;
 @Column(name = "image_base64", columnDefinition = "text", nullable = false) public String imageBase64;
 @Column(name = "thumbnail_base64", columnDefinition = "text", nullable = false) public String thumbnailBase64;
 @Column(nullable = false) public int width;
 @Column(nullable = false) public int height;
 @Column(nullable = false) public int position;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 @Column(nullable = false) public Instant createdAt;
}
