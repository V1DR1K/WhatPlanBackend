package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "why_fun_venue_schedules")
public class WhyFunVenueSchedule {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "venue_id", nullable = false) public WhyFunVenue venue;
 @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false) public DayOfWeek dayOfWeek;
 @Column(name = "opens_at", nullable = false) public LocalTime opensAt;
 @Column(name = "closes_at", nullable = false) public LocalTime closesAt;
}
