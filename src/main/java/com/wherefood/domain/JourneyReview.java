package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_reviews")
public class JourneyReview extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stayId;
    public Long userId;
    public Long memberId;
    public short rating;
    public String comment;
}
