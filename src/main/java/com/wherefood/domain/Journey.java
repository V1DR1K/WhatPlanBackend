package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journeys")
public class Journey extends JourneyEntity {
    public String name;
    public java.time.LocalDate startsOn;
    public java.time.LocalDate endsOn;
    public boolean archived;
    public java.time.Instant createdAt;
    public java.util.UUID coverFileId;
    public int maxTripPhotos = 20;
    public int maxDayPhotos = 10;
}
