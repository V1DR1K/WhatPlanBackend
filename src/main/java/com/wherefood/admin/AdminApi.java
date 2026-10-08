package com.wherefood.admin;

import com.wherefood.domain.User;
import com.wherefood.web.AdminAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApi {
    private final AdminService admin;
    private final AdminAuditService audit;

    public AdminApi(AdminService admin, AdminAuditService audit) {
        this.admin = admin;
        this.audit = audit;
    }

    @GetMapping("/overview")
    AdminService.Overview overview() {
        return admin.overview();
    }

    @GetMapping("/couples")
    List<AdminService.CoupleDetails> couples() {
        return admin.couples();
    }

    @GetMapping("/couples/{id}")
    AdminService.CoupleDetails couple(@PathVariable UUID id) {
        return admin.couple(id);
    }

    @PostMapping("/couples")
    @ResponseStatus(HttpStatus.CREATED)
    AdminService.CoupleDetails createCouple(@AuthenticationPrincipal User actor,
            @Valid @RequestBody CreateCoupleRequest request) {
        return admin.createCouple(actor, request.firstMemberUserId());
    }

    @PostMapping("/couples/{id}/members")
    AdminService.CoupleDetails addMember(@AuthenticationPrincipal User actor, @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request) {
        return admin.addMember(actor, id, request.userId());
    }

    @DeleteMapping("/couples/{id}/members/{userId}")
    AdminService.CoupleDetails removeMember(@AuthenticationPrincipal User actor, @PathVariable UUID id,
            @PathVariable Long userId) {
        return admin.removeMember(actor, id, userId);
    }

    @PatchMapping("/couples/{id}/members/{userId}")
    AdminService.CoupleDetails updateMemberName(@AuthenticationPrincipal User actor, @PathVariable UUID id,
            @PathVariable Long userId, @Valid @RequestBody ChangeMemberNameRequest request) {
        return admin.updateMemberName(actor, id, userId, request.displayName());
    }

    @PostMapping("/couples/{id}/close")
    AdminService.CoupleDetails closeCouple(@AuthenticationPrincipal User actor, @PathVariable UUID id) {
        return admin.closeCouple(actor, id);
    }

    @PatchMapping("/users/{id}/role")
    AdminService.UserSummary updateRole(@AuthenticationPrincipal User actor, @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return admin.updateRole(actor, id, request.role());
    }

    @GetMapping("/users")
    List<AdminService.UserSummary> users() {
        return admin.users();
    }

    @GetMapping("/audit")
    AdminAuditService.AuditPage audit(@RequestParam(required = false) UUID coupleId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return audit.search(coupleId, actorId, page, limit);
    }

    public record CreateCoupleRequest(@NotNull Long firstMemberUserId) {}
    public record AddMemberRequest(@NotNull Long userId) {}
    public record ChangeMemberNameRequest(@NotBlank @Size(max = 100) String displayName) {}
    public record ChangeRoleRequest(@NotBlank @Size(max = 16) String role) {}
}
