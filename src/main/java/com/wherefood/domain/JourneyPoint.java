package com.wherefood.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journey_points")
public class JourneyPoint extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stageId;
    public String title;
    public java.time.LocalDate scheduledOn;
    public java.time.LocalTime scheduledTime;
    public String notes;
    public String address;
    public String mapsUrl;
    public int position;
    public String status = "PENDING";
    @Column(length = 50)
    public String category = "GENERAL";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_actions", columnDefinition = "jsonb", nullable = false)
    public List<JourneyPointAction> extraActions = new ArrayList<>();
    public Long placeId;
    public Long filmId;
    public Long recipeId;
    public Long venueId;
    public Long placeVisitId;
    public Long filmViewId;
    public Long cookingId;
    public Long funVisitId;
}
