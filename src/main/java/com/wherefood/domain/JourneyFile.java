package com.wherefood.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "journey_files")
public class JourneyFile extends JourneyEntity {
    public java.util.UUID journeyId;
    public java.util.UUID stageId;
    public java.util.UUID pointId;
    public java.util.UUID stayId;
    public java.util.UUID movementId;
    public String name;
    public String contentType;
    public long byteSize;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    public byte[] content;

    public java.time.Instant createdAt;
}
