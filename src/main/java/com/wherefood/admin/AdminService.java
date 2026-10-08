package com.wherefood.admin;

import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleInvitationStatus;
import com.wherefood.domain.CoupleMember;
import com.wherefood.domain.CoupleMemberStatus;
import com.wherefood.domain.CoupleStatus;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleInvitations;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Couples;
import com.wherefood.repo.Repositories.Users;
import com.wherefood.web.AdminAuditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {
    private final Users users;
    private final Couples couples;
    private final CoupleMembers members;
    private final CoupleInvitations invitations;
    private final AdminAuditService audit;

    public AdminService(Users users, Couples couples, CoupleMembers members,
            CoupleInvitations invitations, AdminAuditService audit) {
        this.users = users;
        this.couples = couples;
        this.members = members;
        this.invitations = invitations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(users.count(), couples.count(), couples.countByStatus(CoupleStatus.ACTIVE),
                couples.countByStatus(CoupleStatus.PENDING), couples.countByStatus(CoupleStatus.CLOSED),
                members.countByStatus(CoupleMemberStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<CoupleDetails> couples() {
        Map<UUID, List<MemberSummary>> membersByCouple = members.findAllDetailed().stream()
                .collect(java.util.stream.Collectors.groupingBy(member -> member.couple.id, HashMap::new,
                        java.util.stream.Collectors.mapping(AdminService::toMemberSummary,
                                java.util.stream.Collectors.toCollection(ArrayList::new))));
        membersByCouple.values().forEach(values -> values.sort(Comparator.comparing(MemberSummary::slot)));
        return couples.findAllByOrderByCreatedAtDesc().stream()
                .map(couple -> toCoupleDetails(couple, membersByCouple.getOrDefault(couple.id, List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CoupleDetails couple(UUID id) {
        Couple couple = findCouple(id);
        List<MemberSummary> coupleMembers = members.findAllDetailedByCoupleIdOrderBySlot(id).stream()
                .map(AdminService::toMemberSummary).toList();
        return toCoupleDetails(couple, coupleMembers);
    }

    @Transactional
    public CoupleDetails createCouple(User actor, Long firstMemberUserId) {
        User firstMember = lockUser(firstMemberUserId);
        if (members.findActiveCoupleIdByUserId(firstMember.id).isPresent()) {
            throw conflict("El usuario ya pertenece a una pareja activa");
        }
        Couple couple = new Couple();
        couple.createdBy = actor;
        couple.createdAt = Instant.now();
        couple.status = CoupleStatus.PENDING;
        couple = couples.saveAndFlush(couple);
        activateMember(couple, firstMember, 1);
        record(actor, couple.id, "COUPLE_CREATED", "POST", "/api/admin/couples");
        return couple(couple.id);
    }

    @Transactional
    public CoupleDetails addMember(User actor, UUID coupleId, Long userId) {
        Couple couple = lockCouple(coupleId);
        User user = lockUser(userId);
        boolean reopening = couple.status == CoupleStatus.CLOSED;
        if (members.findActiveCoupleIdByUserId(user.id).isPresent()) {
            throw conflict("El usuario ya pertenece a una pareja activa");
        }
        List<CoupleMember> active = members.findByCoupleIdAndStatusOrderBySlot(coupleId, CoupleMemberStatus.ACTIVE);
        if (active.size() >= 2) throw conflict("La pareja ya tiene dos integrantes");
        short slot = active.stream().anyMatch(member -> member.slot == 1) ? (short) 2 : (short) 1;
        activateMember(couple, user, slot);
        couple.status = members.countByCoupleIdAndStatus(coupleId, CoupleMemberStatus.ACTIVE) >= 2
                ? CoupleStatus.ACTIVE : CoupleStatus.PENDING;
        couple.closedAt = null;
        record(actor, couple.id, reopening ? "COUPLE_REOPENED_MEMBER_ADDED" : "COUPLE_MEMBER_ADDED",
                "POST", "/api/admin/couples/{id}/members");
        return couple(coupleId);
    }

    @Transactional
    public CoupleDetails removeMember(User actor, UUID coupleId, Long userId) {
        Couple couple = lockCouple(coupleId);
        CoupleMember member = members.findLockedByCoupleIdAndUserIdAndStatus(coupleId, userId, CoupleMemberStatus.ACTIVE)
                .orElseThrow(() -> notFound("Integrante"));
        member.status = CoupleMemberStatus.LEFT;
        member.leftAt = Instant.now();
        members.flush();
        updateStatusAfterRemoval(couple);
        revokePendingInvitations(coupleId);
        record(actor, couple.id, "COUPLE_MEMBER_REMOVED", "DELETE", "/api/admin/couples/{id}/members/{id}");
        return couple(coupleId);
    }

    @Transactional
    public CoupleDetails updateMemberName(User actor, UUID coupleId, Long userId, String displayName) {
        lockCouple(coupleId);
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre visible debe tener entre 1 y 100 caracteres");
        }
        CoupleMember member = members.findLockedByCoupleIdAndUserIdAndStatus(coupleId, userId, CoupleMemberStatus.ACTIVE)
                .orElseThrow(() -> notFound("Integrante"));
        member.displayName = normalized;
        record(actor, coupleId, "COUPLE_MEMBER_RENAMED", "PATCH", "/api/admin/couples/{id}/members/{id}");
        return couple(coupleId);
    }

    @Transactional
    public CoupleDetails closeCouple(User actor, UUID coupleId) {
        Couple couple = lockCouple(coupleId);
        Instant now = Instant.now();
        members.findByCoupleIdAndStatusOrderBySlot(coupleId, CoupleMemberStatus.ACTIVE).forEach(member -> {
            member.status = CoupleMemberStatus.LEFT;
            member.leftAt = now;
        });
        revokePendingInvitations(coupleId);
        couple.status = CoupleStatus.CLOSED;
        couple.closedAt = now;
        record(actor, couple.id, "COUPLE_CLOSED", "POST", "/api/admin/couples/{id}/close");
        return couple(coupleId);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> users() {
        Map<Long, CoupleMember> activeMembership = new HashMap<>();
        members.findAllDetailedByStatus(CoupleMemberStatus.ACTIVE)
                .forEach(member -> activeMembership.put(member.user.id, member));
        return users.findAllByOrderByCreatedAtDesc().stream().map(user -> {
            CoupleMember membership = activeMembership.get(user.id);
            return new UserSummary(user.id, user.username, user.role.name(), user.createdAt,
                    membership == null ? null : membership.couple.id,
                    membership == null ? null : membership.couple.status.name());
        }).toList();
    }

    @Transactional
    public UserSummary updateRole(User actor, Long userId, String requestedRole) {
        Role role;
        try {
            role = Role.valueOf(requestedRole.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalidRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol debe ser USER o ADMIN");
        }
        User target = users.findLockedById(userId).orElseThrow(() -> notFound("Usuario"));
        if (target.role == Role.ADMIN && role != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw conflict("Debe quedar al menos un administrador activo");
        }
        target.role = role;
        record(actor, null, "USER_ROLE_CHANGED", "PATCH", "/api/admin/users/{id}/role");
        CoupleMember membership = members.findAllDetailedByStatus(CoupleMemberStatus.ACTIVE).stream()
                .filter(value -> value.user.id.equals(target.id)).findFirst().orElse(null);
        return new UserSummary(target.id, target.username, target.role.name(), target.createdAt,
                membership == null ? null : membership.couple.id,
                membership == null ? null : membership.couple.status.name());
    }

    @Transactional(readOnly = true)
    public CoupleStatus statusForScope(UUID id) {
        return findCouple(id).status;
    }

    private void activateMember(Couple couple, User user, int slot) {
        CoupleMember member = new CoupleMember();
        member.couple = couple;
        member.user = user;
        member.displayName = user.username;
        member.slot = (short) slot;
        member.status = CoupleMemberStatus.ACTIVE;
        member.joinedAt = Instant.now();
        members.save(member);
    }

    private void updateStatusAfterRemoval(Couple couple) {
        long remaining = members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE);
        if (remaining == 0) {
            couple.status = CoupleStatus.CLOSED;
            couple.closedAt = Instant.now();
        } else {
            couple.status = CoupleStatus.PENDING;
            couple.closedAt = null;
        }
    }

    private void revokePendingInvitations(UUID coupleId) {
        Instant now = Instant.now();
        invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, CoupleInvitationStatus.PENDING)
                .forEach(invitation -> {
                    invitation.status = CoupleInvitationStatus.REVOKED;
                    invitation.revokedAt = now;
                });
    }

    private Couple lockCouple(UUID id) {
        return couples.findLockedById(id).orElseThrow(() -> notFound("Pareja"));
    }

    private Couple findCouple(UUID id) {
        return couples.findById(id).orElseThrow(() -> notFound("Pareja"));
    }

    private User lockUser(Long id) {
        return users.findLockedById(id).orElseThrow(() -> notFound("Usuario"));
    }

    private void record(User actor, UUID coupleId, String action, String method, String path) {
        audit.record(actor, coupleId, action, method, path,
                "POST".equals(method) && "/api/admin/couples".equals(path)
                        ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
    }

    private static CoupleDetails toCoupleDetails(Couple couple, List<MemberSummary> coupleMembers) {
        return new CoupleDetails(couple.id, couple.status.name(), couple.originCityId,
                couple.createdBy == null ? null : couple.createdBy.username,
                couple.createdAt, couple.closedAt, coupleMembers);
    }

    private static MemberSummary toMemberSummary(CoupleMember member) {
        return new MemberSummary(member.id, member.user.id, member.user.username, member.displayName,
                member.slot, member.status.name(), member.joinedAt, member.leftAt);
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }

    private static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " no encontrado");
    }

    public record Overview(long users, long couples, long activeCouples, long pendingCouples,
                           long closedCouples, long activeMembers) {}
    public record CoupleDetails(UUID id, String status, Long originCityId, String createdBy,
                                Instant createdAt, Instant closedAt, List<MemberSummary> members) {}
    public record MemberSummary(Long membershipId, Long userId, String username, String displayName,
                                short slot, String status, Instant joinedAt, Instant leftAt) {}
    public record UserSummary(Long id, String username, String role, Instant createdAt,
                              UUID coupleId, String coupleStatus) {}
}
