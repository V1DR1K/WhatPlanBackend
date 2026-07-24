package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "special_dates")
public class SpecialDate {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @Column(name = "special_date", nullable = false) public LocalDate date;
 @Column(nullable = false, length = 160) public String label;
 @Column(nullable = false) public Instant createdAt;
 @Column(nullable = false) public Instant updatedAt;
}
