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
    public String purpose;
    public java.time.LocalDate day;
    public String name;
    public String contentType;
    public long byteSize;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    public byte[] content;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "thumbnail_content", columnDefinition = "bytea")
    public byte[] thumbnailContent;

    public Integer width;
    public Integer height;

    public java.time.Instant createdAt;
    public java.time.Instant occurredAt;
}
