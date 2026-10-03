package com.wherefood.domain;

import jakarta.persistence.*;

import java.util.UUID;

@MappedSuperclass
public abstract class JourneyEntity extends CoupleScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Version public long version;
}
