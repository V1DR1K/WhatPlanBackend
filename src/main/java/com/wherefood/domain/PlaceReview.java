package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="place_reviews", uniqueConstraints=@UniqueConstraint(columnNames={"place_id","author_id"}))
public class PlaceReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="place_id") public Place place;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="author_id") public User author;
 public String comment;
 public short location; public short heating; public short bathrooms; public short exterior; public short seating; public short service; public short ambiance;
 public Instant createdAt; public Instant updatedAt;
}
