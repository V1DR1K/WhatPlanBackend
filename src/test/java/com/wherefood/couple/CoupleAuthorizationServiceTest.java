package com.wherefood.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CoupleAuthorizationServiceTest {
    private final CoupleMembers members = org.mockito.Mockito.mock(CoupleMembers.class);
    private final CoupleAuthorizationService authorization = new CoupleAuthorizationService(members);

    @AfterEach
    void clearCoupleContext() {
        CoupleContext.clear();
    }

    @Test
    void resolvePrivateCouple_adminWithoutMembershipReceivesNoPrivateTenantContext() {
        User admin = user(1L, Role.ADMIN);
        when(members.findActiveCoupleIdByUserId(admin.id)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), authorization.resolvePrivateCouple(admin));
        verify(members).findActiveCoupleIdByUserId(admin.id);
    }

    @Test
    void requireActiveMember_adminWithMatchingMembershipReturnsOwnCouple() {
        User admin = user(1L, Role.ADMIN);
        UUID coupleId = UUID.randomUUID();
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(admin.id)).thenReturn(Optional.of(coupleId));

        assertEquals(coupleId, authorization.requireActiveMember(admin));
    }

    @Test
    void requireActiveMember_adminCannotUseAnotherCouplesContext() {
        User admin = user(1L, Role.ADMIN);
        CoupleContext.set(UUID.randomUUID());
        when(members.findActiveCoupleIdByUserId(admin.id)).thenReturn(Optional.of(UUID.randomUUID()));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireActiveMember(admin)).getStatusCode().value());
    }

    @Test
    void requireReviewAuthor_adminMemberCanEditOnlyOwnReview() {
        User admin = user(1L, Role.ADMIN);
        UUID coupleId = UUID.randomUUID();
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(admin.id)).thenReturn(Optional.of(coupleId));

        authorization.requireReviewAuthor(admin.id, admin, "Reseña");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireReviewAuthor(2L, admin, "Reseña")).getStatusCode().value());
    }

    @Test
    void requireActiveMember_whenContextMatchesActiveMembership_returnsCouple() {
        User member = user(2L, Role.USER);
        UUID coupleId = UUID.randomUUID();
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(member.id)).thenReturn(Optional.of(coupleId));

        assertEquals(coupleId, authorization.requireActiveMember(member));
    }

    @Test
    void requireActiveMember_whenContextDoesNotMatchMembership_returns404() {
        User member = user(2L, Role.USER);
        CoupleContext.set(UUID.randomUUID());
        when(members.findActiveCoupleIdByUserId(member.id)).thenReturn(Optional.of(UUID.randomUUID()));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireActiveMember(member)).getStatusCode().value());
    }

    @Test
    void requireReviewAuthor_hidesAnotherMembersReviewAsNotFound() {
        User member = user(2L, Role.USER);
        UUID coupleId = UUID.randomUUID();
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(member.id)).thenReturn(Optional.of(coupleId));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireReviewAuthor(3L, member, "Reseña")).getStatusCode().value());
    }

    @Test
    void requireReviewAuthor_withoutAnActiveCouple_returns404() {
        User member = user(2L, Role.USER);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireReviewAuthor(member.id, member, "Reseña")).getStatusCode().value());
    }

    @Test
    void requireReviewAuthor_doesNotLetAdminEditPrivateReview() {
        User admin = user(2L, Role.ADMIN);

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> authorization.requireReviewAuthor(admin.id, admin, "Reseña")).getStatusCode().value());
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        return user;
    }
}
