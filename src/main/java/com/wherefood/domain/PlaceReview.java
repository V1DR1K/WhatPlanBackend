package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="place_reviews", uniqueConstraints=@UniqueConstraint(columnNames={"place_id","author_id"}))
public class PlaceReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="place_id") public Place place;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="author_id") public User author;
 public String comment;
 public Short location; public Short heating; public Short bathrooms; public Short exterior; public Short seating; public Short service; public Short ambiance;
 public Instant createdAt; public Instant updatedAt;
}
