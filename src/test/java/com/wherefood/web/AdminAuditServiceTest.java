package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.AdminAuditEvent;
import com.wherefood.repo.Repositories.AdminAuditEvents;
import com.wherefood.repo.Repositories.Users;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AdminAuditServiceTest {
    private final AdminAuditEvents events = mock(AdminAuditEvents.class);
    private final AdminAuditService service = new AdminAuditService(events, mock(Users.class));

    @Test
    void searchUsesRequestedPageAndCapsThePageSize() {
        UUID coupleId = UUID.randomUUID();
        when(events.search(eq(coupleId), eq(7L), any(Pageable.class))).thenAnswer(invocation ->
                new PageImpl<AdminAuditEvent>(List.of(), invocation.getArgument(2), 250));

        AdminAuditService.AuditPage result = service.search(coupleId, 7L, 3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).search(eq(coupleId), eq(7L), pageable.capture());
        assertEquals(3, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(3, result.page());
        assertEquals(100, result.limit());
        assertEquals(250, result.total());
        assertEquals(3, result.totalPages());
    }

    @Test
    void searchClampsNegativePageAndMinimumPageSize() {
        UUID coupleId = UUID.randomUUID();
        when(events.search(eq(coupleId), eq(7L), any(Pageable.class))).thenAnswer(invocation ->
                new PageImpl<AdminAuditEvent>(List.of(), invocation.getArgument(2), 0));

        AdminAuditService.AuditPage result = service.search(coupleId, 7L, -4, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).search(eq(coupleId), eq(7L), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(1, pageable.getValue().getPageSize());
        assertEquals(0, result.page());
        assertEquals(1, result.limit());
        assertEquals(0, result.totalPages());
    }
}
