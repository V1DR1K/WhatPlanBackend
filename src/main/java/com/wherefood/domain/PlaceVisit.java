package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="place_visits")
public class PlaceVisit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="place_id", nullable=false) public Place place;
 @Column(name="visited_on", nullable=false) public LocalDate visitedOn;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by", nullable=false) public User createdBy;
 public Instant createdAt;
 public Instant updatedAt;
}
