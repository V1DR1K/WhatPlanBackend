package com.wherefood.web;

import com.wherefood.config.AdminCoupleContext;
import com.wherefood.config.CoupleContext;
import com.wherefood.domain.AdminAuditEvent;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.AdminAuditEvents;
import com.wherefood.repo.Repositories.Users;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final AdminAuditEvents events;
    private final Users users;

    public AdminAuditService(AdminAuditEvents events, Users users) {
        this.events = events;
        this.users = users;
    }

    @Transactional
    public void record(User actor, UUID coupleId, String action, String method, String path, int status) {
        if (actor == null || actor.id == null) return;
        AdminAuditEvent event = new AdminAuditEvent();
        event.actor = users.getReferenceById(actor.id);
        event.actorUsername = actor.username;
        event.coupleId = coupleId;
        event.action = action;
        event.httpMethod = method;
        event.requestPath = path;
        event.responseStatus = (short) Math.max(100, Math.min(status, 599));
        event.occurredAt = Instant.now();
        events.save(event);
    }

    public void recordCurrent(User actor, String action, String method, String path, int status) {
        UUID coupleId = AdminCoupleContext.current();
        if (coupleId == null) coupleId = CoupleContext.current();
        record(actor, coupleId, action, method, path, status);
    }

    @Transactional(readOnly = true)
    public AuditPage search(UUID coupleId, Long actorId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        var rows = events.search(coupleId, actorId,
                PageRequest.of(0, limit, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new AuditPage(rows.getContent().stream().map(value -> new AuditEntry(
                value.id, value.actor.id, value.actorUsername, value.coupleId, value.action,
                value.httpMethod, value.requestPath, value.responseStatus, value.occurredAt)).toList(),
                rows.getTotalElements(), limit);
    }

    public record AuditEntry(Long id, Long actorUserId, String actorUsername, UUID coupleId,
                             String action, String method, String path, short status, Instant occurredAt) {}
    public record AuditPage(java.util.List<AuditEntry> entries, long total, int limit) {}
}
