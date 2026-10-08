package com.wherefood.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Couple;
import com.wherefood.domain.CoupleMember;
import com.wherefood.domain.CoupleMemberStatus;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleInvitations;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.Couples;
import com.wherefood.repo.Repositories.Users;
import com.wherefood.web.AdminAuditService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminServiceTest {
    private final Users users = mock(Users.class);
    private final Couples couples = mock(Couples.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final CoupleInvitations invitations = mock(CoupleInvitations.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminService service = new AdminService(users, couples, members, invitations, audit);

    @Test
    void refusesToAddAThirdActiveMemberToACouple() {
        UUID coupleId = UUID.randomUUID();
        Couple couple = new Couple();
        couple.id = coupleId;
        User candidate = new User();
        candidate.id = 3L;
        when(couples.findLockedById(coupleId)).thenReturn(Optional.of(couple));
        when(users.findLockedById(3L)).thenReturn(Optional.of(candidate));
        when(members.findActiveCoupleIdByUserId(3L)).thenReturn(Optional.empty());
        CoupleMember first = new CoupleMember();
        first.slot = 1;
        CoupleMember second = new CoupleMember();
        second.slot = 2;
        when(members.findByCoupleIdAndStatusOrderBySlot(coupleId, CoupleMemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.addMember(new User(), coupleId, 3L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(members, never()).save(any(CoupleMember.class));
    }

    @Test
    void preservesTheLastAdministrator() {
        User lastAdmin = new User();
        lastAdmin.id = 1L;
        lastAdmin.role = Role.ADMIN;
        when(users.findLockedById(1L)).thenReturn(Optional.of(lastAdmin));
        when(users.countByRole(Role.ADMIN)).thenReturn(1L);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.updateRole(new User(), 1L, "USER"));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(Role.ADMIN, lastAdmin.role);
        verify(users).findLockedByRole(Role.ADMIN);
    }
}
