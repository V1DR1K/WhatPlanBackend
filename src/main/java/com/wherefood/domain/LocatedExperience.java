package com.wherefood.domain;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class LocatedExperience extends CoupleScopedEntity {
    @Column(name = "city_id", nullable = false)
    public Long cityId;

    @Column(name = "stage_id")
    public java.util.UUID stageId;
}
