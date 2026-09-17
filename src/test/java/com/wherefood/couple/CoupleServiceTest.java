package com.wherefood.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleInvitation;
import com.wherefood.domain.CoupleInvitationStatus;
import com.wherefood.domain.CoupleMember;
import com.wherefood.domain.CoupleMemberStatus;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleInvitations;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Couples;
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
    private CoupleService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CoupleService(couples, members, invitations);
        user = new User();
        user.id = 10L;
        user.username = "new-user";
    }

    @Test
    void createsPrivateCoupleForUserWithoutOne() {
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
        invitation.couple = couple;
        invitation.status = CoupleInvitationStatus.PENDING;
        invitation.expiresAt = Instant.now().minusSeconds(1);
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.empty());
        when(invitations.findByTokenHash(any())).thenReturn(Optional.of(invitation));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(invitations.findLockedById(any())).thenReturn(Optional.of(invitation));

        assertThrows(ResponseStatusException.class, () -> service.accept("token", user));
        assertEquals(CoupleInvitationStatus.EXPIRED, invitation.status);
    }

    @Test
    void createsSingleUseInvitationWithSevenDayExpiry() {
        Couple couple = couple();
        when(members.findActiveCoupleIdByUserId(user.id)).thenReturn(Optional.of(couple.id));
        when(couples.findLockedById(couple.id)).thenReturn(Optional.of(couple));
        when(members.countByCoupleIdAndStatus(couple.id, CoupleMemberStatus.ACTIVE)).thenReturn(1L);
        when(invitations.findByCoupleIdAndStatusOrderByCreatedAtDesc(couple.id, CoupleInvitationStatus.PENDING)).thenReturn(List.of());

        CoupleService.InvitationSnapshot result = service.createInvitation(user);

        assertNotNull(result.token());
        assertEquals(43, result.token().length());
        assertEquals(CoupleInvitationStatus.PENDING, capturedStatus());
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
}
