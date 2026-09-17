package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couples")
public class Couple {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CoupleStatus status = CoupleStatus.PENDING;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    public User createdBy;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant closedAt;

    @Version
    public long version;
}
