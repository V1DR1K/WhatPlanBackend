package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "special_date_occurrences", uniqueConstraints = @UniqueConstraint(columnNames = {"special_date_id", "occurred_on"}))
public class SpecialDateOccurrence {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Version public long version;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "special_date_id", nullable = false) public SpecialDate specialDate;
 @Column(name = "occurred_on", nullable = false) public LocalDate occurredOn;
 @Column(name = "cover_photo_id") public Long coverPhotoId;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false) public User createdBy;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "updated_by", nullable = false) public User updatedBy;
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
