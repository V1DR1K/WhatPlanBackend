package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_stages")
public class JourneyStage extends JourneyEntity {
    public java.util.UUID journeyId;
    public Long cityId;
    public java.time.LocalDate startsOn;
    public java.time.LocalDate endsOn;
    public int position;
}
