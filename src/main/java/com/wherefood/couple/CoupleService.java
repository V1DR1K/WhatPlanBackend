package com.wherefood.couple;

import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleInvitation;
import com.wherefood.domain.CoupleInvitationStatus;
import com.wherefood.domain.CoupleMember;
import com.wherefood.domain.CoupleMemberStatus;
import com.wherefood.domain.CoupleStatus;
import com.wherefood.domain.User;
import com.wherefood.config.CoupleContext;
import com.wherefood.repo.Repositories.CoupleInvitations;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Couples;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CoupleService {
    private static final Logger audit = LoggerFactory.getLogger("whatplan.audit.couple");
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private final Couples couples;
    private final CoupleMembers members;
    private final CoupleInvitations invitations;
    private final SecureRandom random = new SecureRandom();

    public CoupleService(Couples couples, CoupleMembers members, CoupleInvitations invitations) {
        this.couples = couples;
        this.members = members;
        this.invitations = invitations;
    }

    @Transactional(readOnly = true)
    public CoupleSnapshot current(User user) {
        UUID coupleId = members.findActiveCoupleIdByUserId(user.id).orElse(null);
        if (coupleId == null) return CoupleSnapshot.none();
        Couple couple = couples.findById(coupleId).orElseThrow(() -> notFound("Pareja"));
        List<CoupleMember> coupleMembers = members.findByCoupleIdAndStatusOrderBySlot(coupleId, CoupleMemberStatus.ACTIVE);
        CoupleInvitation pending = invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, CoupleInvitationStatus.PENDING)
                .stream().filter(value -> value.expiresAt.isAfter(Instant.now())).findFirst().orElse(null);
        return snapshot(couple, coupleMembers, pending, user);
    }

    @Transactional
    public CoupleSnapshot create(User user) {
        if (members.findActiveCoupleIdByUserId(user.id).isPresent()) throw conflict("Ya pertenecés a una pareja activa");
        Couple couple = new Couple();
        couple.createdBy = user;
        couple.createdAt = Instant.now();
        couple.status = CoupleStatus.PENDING;
        couples.save(couple);
        CoupleContext.set(couple.id);
        addMember(couple, user, (short) 1);
        audit.info("couple_created coupleId={} userId={} status={}", couple.id, user.id, couple.status);
        return snapshot(couple, members.findByCoupleIdAndStatusOrderBySlot(couple.id, CoupleMemberStatus.ACTIVE), null, user);
    }

    @Transactional
    public InvitationSnapshot createInvitation(User user) {
        UUID coupleId = activeCoupleId(user);
        Couple couple = couples.findLockedById(coupleId).orElseThrow(() -> notFound("Pareja"));
        if (members.countByCoupleIdAndStatus(coupleId, CoupleMemberStatus.ACTIVE) >= 2) throw conflict("La pareja ya tiene dos integrantes");
        Instant now = Instant.now();
        invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, CoupleInvitationStatus.PENDING).forEach(value -> {
            value.status = CoupleInvitationStatus.REVOKED;
            value.revokedAt = now;
        });
        String token = randomToken();
        CoupleInvitation invitation = new CoupleInvitation();
        invitation.couple = couple;
        invitation.createdBy = user;
        invitation.tokenHash = hash(token);
        invitation.expiresAt = now.plus(INVITATION_TTL);
        invitation.createdAt = now;
        invitations.save(invitation);
        audit.info("couple_invitation_created coupleId={} invitationId={} userId={} expiresAt={}", coupleId, invitation.id, user.id, invitation.expiresAt);
        return new InvitationSnapshot(invitation.id, token, invitation.expiresAt);
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public CoupleSnapshot accept(String rawToken, User user) {
        if (rawToken == null || rawToken.isBlank()) throw notFound("Invitación");
        if (members.findActiveCoupleIdByUserId(user.id).isPresent()) throw conflict("Primero tenés que dejar tu pareja actual");
        CoupleInvitation invitation = invitations.findByTokenHash(hash(rawToken.trim())).orElseThrow(() -> notFound("Invitación"));
        Couple couple = couples.findLockedById(invitation.couple.id).orElseThrow(() -> notFound("Pareja"));
        CoupleContext.set(couple.id);
        invitation = invitations.findLockedById(invitation.id).orElseThrow(() -> notFound("Invitación"));
        Instant now = Instant.now();
        if (invitation.status != CoupleInvitationStatus.PENDING) throw notFound("Invitación");
        if (!invitation.expiresAt.isAfter(now)) {
            invitation.status = CoupleInvitationStatus.EXPIRED;
            audit.info("couple_invitation_expired invitationId={} userId={}", invitation.id, user.id);
            throw new ExpiredInvitationException();
        }
        if (members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE) >= 2) throw conflict("La pareja ya está completa");
        short slot = members.findByCoupleIdAndStatusOrderBySlot(couple.id, CoupleMemberStatus.ACTIVE).stream()
                .map(member -> member.slot)
                .filter(value -> value == 1)
                .findFirst()
                .isEmpty() ? (short) 1 : (short) 2;
        addMember(couple, user, slot);
        couple.status = CoupleStatus.ACTIVE;
        invitation.status = CoupleInvitationStatus.ACCEPTED;
        invitation.acceptedBy = user;
        invitation.acceptedAt = now;
        invitations.save(invitation);
        audit.info("couple_invitation_accepted coupleId={} invitationId={} userId={}", couple.id, invitation.id, user.id);
        return snapshot(couple, members.findByCoupleIdAndStatusOrderBySlot(couple.id, CoupleMemberStatus.ACTIVE), null, user);
    }

    @Transactional
    public void revoke(Long invitationId, User user) {
        UUID coupleId = activeCoupleId(user);
        CoupleInvitation invitation = invitations.findByIdAndCoupleId(invitationId, coupleId).orElseThrow(() -> notFound("Invitación"));
        if (invitation.status == CoupleInvitationStatus.PENDING) {
            invitation.status = CoupleInvitationStatus.REVOKED;
            invitation.revokedAt = Instant.now();
            audit.info("couple_invitation_revoked coupleId={} invitationId={} userId={}", coupleId, invitation.id, user.id);
        }
    }

    @Transactional
    public void leave(User user) {
        UUID coupleId = activeCoupleId(user);
        Couple couple = couples.findLockedById(coupleId).orElseThrow(() -> notFound("Pareja"));
        CoupleMember member = members.findByCoupleIdAndUserIdAndStatus(coupleId, user.id, CoupleMemberStatus.ACTIVE)
                .orElseThrow(() -> notFound("Integrante"));
        member.status = CoupleMemberStatus.LEFT;
        member.leftAt = Instant.now();
        invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(coupleId, CoupleInvitationStatus.PENDING).forEach(value -> {
            value.status = CoupleInvitationStatus.REVOKED;
            value.revokedAt = Instant.now();
        });
        if (members.countByCoupleIdAndStatus(coupleId, CoupleMemberStatus.ACTIVE) == 0) {
            couple.status = CoupleStatus.CLOSED;
            couple.closedAt = Instant.now();
        }
        audit.info("couple_member_left coupleId={} userId={} status={}", coupleId, user.id, couple.status);
    }

    private void addMember(Couple couple, User user, short slot) {
        CoupleMember member = new CoupleMember();
        member.couple = couple;
        member.user = user;
        member.displayName = user.username;
        member.slot = slot;
        member.status = CoupleMemberStatus.ACTIVE;
        member.joinedAt = Instant.now();
        members.save(member);
    }

    private UUID activeCoupleId(User user) {
        return members.findActiveCoupleIdByUserId(user.id).orElseThrow(() -> conflict("Primero creá o aceptá una pareja"));
    }

    private static CoupleSnapshot snapshot(Couple couple, List<CoupleMember> members, CoupleInvitation invitation, User current) {
        return new CoupleSnapshot(couple.id, couple.status.name(), members.stream().map(member -> new MemberSnapshot(
                member.id, member.user.id, member.displayName, member.user.username, member.user.id.equals(current.id))).toList(),
                invitation == null ? null : new InvitationSnapshot(invitation.id, null, invitation.expiresAt));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message + " no encontrada"); }

    private static final class ExpiredInvitationException extends ResponseStatusException {
        private ExpiredInvitationException() { super(HttpStatus.NOT_FOUND, "Invitación no encontrada"); }
    }

    public record MemberSnapshot(Long id, Long userId, String displayName, String username, boolean current) {}
    public record CoupleSnapshot(UUID id, String status, List<MemberSnapshot> members, InvitationSnapshot pendingInvitation) {
        static CoupleSnapshot none() { return new CoupleSnapshot(null, "NONE", List.of(), null); }
    }
    public record InvitationSnapshot(Long id, String token, Instant expiresAt) {}
}
