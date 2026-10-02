package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import com.wherefood.repo.Repositories.SpecialDates;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialDateServiceTest {
    private final SpecialDates dates = mock(SpecialDates.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final SpecialDateService service = new SpecialDateService(dates,
            new CoupleAuthorizationService(members));

    @BeforeEach
    void establishMemberContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(8L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearContext() {
        CoupleContext.clear();
    }

    @Test
    void update_whenDateIsFromAnotherCouple_returns404WithoutSaving() {
        when(dates.findByIdAndCoupleId(19L, coupleId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(19L, new SpecialDateRequest(LocalDate.now(), "Aniversario",
                        com.wherefood.domain.SpecialDateRecurrence.ANNUAL), user()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(dates, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static User user() {
        User value = new User();
        value.id = 8L;
        value.role = Role.USER;
        return value;
    }
}
