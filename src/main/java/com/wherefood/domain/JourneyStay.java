package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_stays")
public class JourneyStay extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stageId;
    public String name;
    public java.time.LocalDate startsOn;
    public java.time.LocalDate endsOn;
    public String address;
    public java.math.BigDecimal price;
    public String currency;
    public String source;
    public String bookingUrl;
    public String mapsUrl;
    public java.util.UUID photoId;
}
