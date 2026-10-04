package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "journey_point_types")
public class JourneyPointType extends CoupleScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 50)
    public String code;

    @Column(nullable = false, length = 80)
    public String name;

    @Column(nullable = false, length = 20)
    public String icon;

    @Column(nullable = false, length = 7)
    public String color;

    @Column(nullable = false)
    public int position;

    @Column(name = "built_in", nullable = false)
    public boolean builtIn;

    @Version
    public long version;
}
