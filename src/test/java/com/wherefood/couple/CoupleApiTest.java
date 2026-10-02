package com.wherefood.couple;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoupleApiTest {
    private final CoupleService service = mock(CoupleService.class);
    private final CoupleApi api = new CoupleApi(service);

    @Test
    void acceptsAnInvitationSecretFromTheRequestBodyRatherThanTheUrl() {
        String token = "a".repeat(43);
        User user = new User();
        CoupleService.CoupleSnapshot snapshot = new CoupleService.CoupleSnapshot(UUID.randomUUID(), "ACTIVE", List.of(), null);
        when(service.accept(token, user)).thenReturn(snapshot);

        CoupleService.CoupleSnapshot result = api.accept(new AcceptInvitationRequest(token), user);

        org.junit.jupiter.api.Assertions.assertEquals(snapshot, result);
        verify(service).accept(token, user);
    }
}
