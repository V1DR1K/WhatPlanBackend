package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "journey_day_reviews")
public class JourneyDayReview extends JourneyEntity {
    public UUID journeyId;
    public LocalDate day;
    public Long userId;
    public Long memberId;
    public Short rating;
    public String comment;
    public Instant updatedAt;
}
