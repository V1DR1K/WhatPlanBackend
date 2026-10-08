package com.wherefood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    public User actor;

    @Column(name = "actor_username", nullable = false, length = 80)
    public String actorUsername;

    @Column(name = "couple_id")
    public UUID coupleId;

    @Column(nullable = false, length = 40)
    public String action;

    @Column(name = "http_method", nullable = false, length = 10)
    public String httpMethod;

    @Column(name = "request_path", nullable = false, length = 300)
    public String requestPath;

    @Column(name = "response_status", nullable = false)
    public short responseStatus;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
