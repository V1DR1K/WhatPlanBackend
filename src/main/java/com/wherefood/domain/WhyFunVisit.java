package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "why_fun_visits")
public class WhyFunVisit {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venue_id", nullable = false) public WhyFunVenue venue;
 @Column(name = "scheduled_at") public LocalDate scheduledAt;
 @Column(name = "cover_photo_id") public Long coverPhotoId;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 public Instant createdAt;
 public Instant updatedAt;
}
