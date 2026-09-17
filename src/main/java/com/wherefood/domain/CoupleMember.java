package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "couple_members", uniqueConstraints = @UniqueConstraint(columnNames = {"couple_id", "user_id"}))
public class CoupleMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "couple_id", nullable = false)
    public Couple couple;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "display_name", nullable = false, length = 100)
    public String displayName;

    @Column(nullable = false)
    public short slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CoupleMemberStatus status = CoupleMemberStatus.ACTIVE;

    @Column(nullable = false)
    public Instant joinedAt;

    public Instant leftAt;

    @Version
    public long version;
}
