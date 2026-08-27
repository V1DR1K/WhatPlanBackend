package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "why_fun_visit_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"visit_id", "author_id"}))
public class WhyFunVisitReview {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "visit_id", nullable = false) public WhyFunVisit visit;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) public User author;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(nullable = false) public short rating;
 @Column(length = 1000) public String comment;
 public Instant createdAt;
 public Instant updatedAt;
}
