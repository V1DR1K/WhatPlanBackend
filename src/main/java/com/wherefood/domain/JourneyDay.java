package com.wherefood.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "journey_days")
public class JourneyDay extends JourneyEntity {
    public UUID journeyId;
    public LocalDate day;
    public String story;
}
