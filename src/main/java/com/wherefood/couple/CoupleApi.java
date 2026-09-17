package com.wherefood.couple;

import com.wherefood.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CoupleApi {
    private final CoupleService couples;

    public CoupleApi(CoupleService couples) {
        this.couples = couples;
    }

    @GetMapping("/couple")
    CoupleService.CoupleSnapshot current(@AuthenticationPrincipal User user) {
        return couples.current(user);
    }

    @PostMapping("/couples")
    @ResponseStatus(HttpStatus.CREATED)
    CoupleService.CoupleSnapshot create(@AuthenticationPrincipal User user) {
        return couples.create(user);
    }

    @PostMapping("/couple/invitations")
    CoupleService.InvitationSnapshot createInvitation(@AuthenticationPrincipal User user) {
        return couples.createInvitation(user);
    }

    @DeleteMapping("/couple/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvitation(@PathVariable Long id, @AuthenticationPrincipal User user) {
        couples.revoke(id, user);
    }

    @PostMapping("/couple/invitations/{token}/accept")
    CoupleService.CoupleSnapshot accept(@PathVariable String token, @AuthenticationPrincipal User user) {
        return couples.accept(token, user);
    }

    @PostMapping("/couple/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal User user) {
        couples.leave(user);
    }
}
