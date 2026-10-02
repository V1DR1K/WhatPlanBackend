package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/** Common persisted tenant key for private content. The database default owns writes. */
@MappedSuperclass
public abstract class CoupleScopedEntity {
    @Column(name = "couple_id", nullable = false, insertable = false, updatable = false)
    public UUID coupleId;
}
