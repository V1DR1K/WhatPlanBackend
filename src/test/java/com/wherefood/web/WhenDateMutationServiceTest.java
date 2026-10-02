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
import com.wherefood.repo.Repositories.SpecialDateOccurrenceComments;
import com.wherefood.repo.Repositories.SpecialDateOccurrencePhotos;
import com.wherefood.repo.Repositories.SpecialDateOccurrences;
import com.wherefood.repo.Repositories.SpecialDates;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WhenDateMutationServiceTest {
    private final SpecialDates dates = mock(SpecialDates.class);
    private final SpecialDateOccurrences occurrences = mock(SpecialDateOccurrences.class);
    private final SpecialDateOccurrenceComments comments = mock(SpecialDateOccurrenceComments.class);
    private final SpecialDateOccurrencePhotos photos = mock(SpecialDateOccurrencePhotos.class);
    private final CoupleMembers members = mock(CoupleMembers.class);
    private final UUID coupleId = UUID.randomUUID();
    private final WhenDateMutationService service = new WhenDateMutationService(dates, occurrences,
            comments, photos, mock(PhotoStorage.class), new CoupleAuthorizationService(members));

    @BeforeEach
    void establishMemberContext() {
        CoupleContext.set(coupleId);
        when(members.findActiveCoupleIdByUserId(9L)).thenReturn(Optional.of(coupleId));
    }

    @AfterEach
    void clearContext() {
        CoupleContext.clear();
    }

    @Test
    void saveComment_whenDateBelongsToAnotherCouple_returns404BeforeCreatingOccurrence() {
        when(dates.findByIdAndCoupleId(22L, coupleId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.saveComment(22L, LocalDate.of(2025, 4, 10),
                        new WhenDateCommentRequest("recuerdo"), user()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(occurrences, never()).save(org.mockito.ArgumentMatchers.any());
        verify(comments, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static User user() {
        User value = new User();
        value.id = 9L;
        value.role = Role.USER;
        return value;
    }
}
