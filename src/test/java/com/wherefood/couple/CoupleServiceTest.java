package com.wherefood.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleInvitation;
import com.wherefood.domain.CoupleInvitationStatus;
import com.wherefood.domain.CoupleMember;
import com.wherefood.domain.CoupleMemberStatus;
import com.wherefood.domain.CoupleStatus;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleInvitations;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Couples;
import com.wherefood.repo.Repositories.InvitationLocator;
import com.wherefood.repo.Repositories.Users;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CoupleServiceTest {
    @Mock Couples couples;
    @Mock CoupleMembers members;
    @Mock CoupleInvitations invitations;
    @Mock Users users;
    private CoupleService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CoupleService(couples, members, invitations, users);
        user = new User();
        user.id = 10L;
        user.username = "new-user";
    }

    @Test
    void createsPrivateCoupleForUserWithoutOne() {
        when(users.findLockedById(user.id)).thenReturn(Optional.of(user));
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.empty());
        when(couples.save(any())).thenAnswer(invocation -> {
            Couple value = invocation.getArgument(0);
            value.id = UUID.randomUUID();
            return value;
        });
        CoupleMember member = new CoupleMember();
        member.id = 1L;
        member.user = user;
        member.displayName = user.username;
        when(members.findByCoupleIdAndStatusOrderBySlot(any(), any())).thenReturn(List.of(member));

        CoupleService.CoupleSnapshot result = service.create(user);

        assertNotNull(result.id());
        assertEquals("PENDING", result.status());
        assertEquals(1, result.members().size());
    }

    @Test
    void rejectsExpiredInvitationAndMarksItExpired() {
        Couple couple = couple();
        CoupleInvitation invitation = new CoupleInvitation();
        invitation.id = 15L;
        invitation.couple = couple;
        invitation.status = CoupleInvitationStatus.PENDING;
        invitation.expiresAt = Instant.now().minusSeconds(1);
        when(users.findLockedById(user.id)).thenReturn(Optional.of(user));
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.empty());
        InvitationLocator locator = mock(InvitationLocator.class);
        when(locator.getId()).thenReturn(invitation.id);
        when(locator.getCoupleId()).thenReturn(couple.id);
        when(invitations.findInvitationLocatorByTokenHash(any())).thenReturn(Optional.of(locator));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(invitations.findLockedByIdAndCoupleId(invitation.id, couple.id)).thenReturn(Optional.of(invitation));

        assertThrows(ResponseStatusException.class, () -> service.accept("a".repeat(43), user));
        assertEquals(CoupleInvitationStatus.EXPIRED, invitation.status);
    }

    @Test
    void createsSingleUseInvitationWithSevenDayExpiry() {
        Couple couple = couple();
        when(members.findLockedByCoupleIdAndUserIdAndStatus(couple.id, user.id, CoupleMemberStatus.ACTIVE))
                .thenReturn(Optional.of(activeMember(couple, user)));
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE)).thenReturn(1L);
        when(invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(couple.id, CoupleInvitationStatus.PENDING)).thenReturn(List.of());

        CoupleService.InvitationSnapshot result = service.createInvitation(user);

        assertNotNull(result.token());
        assertEquals(43, result.token().length());
        assertEquals(CoupleInvitationStatus.PENDING, capturedStatus());
    }

    @Test
    void boundsInvitationCreationForOneCoupleToTenPerRollingDay() {
        Couple couple = couple();
        when(members.findLockedByCoupleIdAndUserIdAndStatus(couple.id, user.id, CoupleMemberStatus.ACTIVE))
                .thenReturn(Optional.of(activeMember(couple, user)));
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE)).thenReturn(1L);
        when(invitations.countByCoupleIdAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(couple.id), any(Instant.class)))
                .thenReturn(10L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.createInvitation(user));

        assertEquals(429, error.getStatusCode().value());
    }

    @Test
    void refusesInvitationCreationIfMembershipEndedWhileWaitingForCoupleLock() {
        Couple couple = couple();
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.findLockedByCoupleIdAndUserIdAndStatus(couple.id, user.id, CoupleMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createInvitation(user));

        assertEquals(404, error.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(invitations);
    }

    @Test
    void refusesInvitationRevocationIfMembershipEndedWhileWaitingForCoupleLock() {
        Couple couple = couple();
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.findLockedByCoupleIdAndUserIdAndStatus(couple.id, user.id, CoupleMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.revoke(12L, user));

        assertEquals(404, error.getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(invitations);
    }

    @Test
    void rejectsMalformedInvitationSecretsBeforeAnyRepositoryLookup() {
        assertThrows(ResponseStatusException.class, () -> service.accept("x".repeat(100_000), user));
        org.mockito.Mockito.verifyNoInteractions(couples, members, invitations, users);
    }

    @Test
    void leavingKeepsOpenPairWhileOneMemberRemainsAndClosesAfterTheLastLeaves() {
        Couple couple = couple();
        couple.status = CoupleStatus.ACTIVE;
        CoupleMember member = new CoupleMember();
        member.couple = couple;
        member.user = user;
        member.status = CoupleMemberStatus.ACTIVE;
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.findLockedByCoupleIdAndUserIdAndStatus(couple.id, user.id, CoupleMemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(couple.id, CoupleInvitationStatus.PENDING))
                .thenReturn(List.of());
        when(members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE)).thenReturn(1L, 0L);

        service.leave(user);
        assertEquals(CoupleStatus.ACTIVE, couple.status);
        assertNull(couple.closedAt);

        service.leave(user);
        assertEquals(CoupleStatus.CLOSED, couple.status);
        assertNotNull(couple.closedAt);
        assertEquals(CoupleMemberStatus.LEFT, member.status);
    }

    private CoupleInvitationStatus capturedStatus() {
        var invitation = org.mockito.ArgumentCaptor.forClass(CoupleInvitation.class);
        org.mockito.Mockito.verify(invitations).save(invitation.capture());
        return invitation.getValue().status;
    }

    private Couple couple() {
        Couple couple = new Couple();
        couple.id = UUID.randomUUID();
        return couple;
    }

    private static CoupleMember activeMember(Couple couple, User user) {
        CoupleMember member = new CoupleMember();
        member.id = 1L;
        member.couple = couple;
        member.user = user;
        member.status = CoupleMemberStatus.ACTIVE;
        return member;
    }
}
