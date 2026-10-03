package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_points")
public class JourneyPoint extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stageId;
    public String title;
    public java.time.LocalDate scheduledOn;
    public java.time.LocalTime scheduledTime;
    public String notes;
    public String mapsUrl;
    public int position;
    public String status = "PENDING";
    public Long placeId;
    public Long filmId;
    public Long recipeId;
    public Long venueId;
    public Long placeVisitId;
    public Long filmViewId;
    public Long cookingId;
    public Long funVisitId;
}
