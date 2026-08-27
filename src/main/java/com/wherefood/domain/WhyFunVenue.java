package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "why_fun_venues")
public class WhyFunVenue {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @Column(nullable = false) public String name;
 @Column(nullable = false) public String address;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id", nullable = false) public WhyFunCategory category;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subcategory_id", nullable = false) public WhyFunCategory subcategory;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(name = "scheduled_at") public LocalDate scheduledAt;
 @Column(name = "cover_photo_id") public Long coverPhotoId;
 @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true) public List<WhyFunVenueSchedule> schedules = new ArrayList<>();
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
