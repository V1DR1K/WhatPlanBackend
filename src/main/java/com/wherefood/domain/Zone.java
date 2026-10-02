package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "zones")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Version
    public long version;
    @Column(nullable = false, unique = true, length = 80)
    public String name;
    @Column(nullable = false)
    public boolean active = true;
    @Column(nullable = false)
    public Instant createdAt;
    @Column(nullable = false)
    public Instant updatedAt;
}
