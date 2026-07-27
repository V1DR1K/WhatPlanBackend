package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "special_date_occurrence_comments", uniqueConstraints = @UniqueConstraint(columnNames = {"occurrence_id", "author_id"}))
public class SpecialDateOccurrenceComment {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "occurrence_id", nullable = false) public SpecialDateOccurrence occurrence;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) public User author;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(nullable = false, length = 2000) public String comment;
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
