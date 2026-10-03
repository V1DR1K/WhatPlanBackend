package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_movements")
public class JourneyMovement extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stageId;
    public java.util.UUID pointId;
    public java.util.UUID stayId;
    public String kind;
    public String description;
    public java.math.BigDecimal amount;
    public String currency;
    public java.time.LocalDate occurredOn;
}
