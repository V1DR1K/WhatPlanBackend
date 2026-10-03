package com.wherefood.couple;

import com.wherefood.config.CoupleContext;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.CoupleMembers;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Private content requires membership; catalog administration never bypasses that boundary. */
@Service
public class CoupleAuthorizationService {
    private final CoupleMembers members;

    public CoupleAuthorizationService(CoupleMembers members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolvePrivateCouple(User user) {
        if (user == null || members == null) return Optional.empty();
        return members.findActiveCoupleIdByUserId(user.id);
    }

    @Transactional(readOnly = true)
    public UUID requireActiveMember(User user) {
        UUID contextCoupleId = CoupleContext.current();
        UUID activeCoupleId = resolvePrivateCouple(user)
                .filter(id -> id.equals(contextCoupleId))
                .orElseThrow(CoupleAuthorizationService::notFound);
        return activeCoupleId;
    }

    public void requireReviewAuthor(Long ownerId, User actor, String resourceName) {
        if (actor == null || ownerId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, resourceName + " no encontrada");
        }
        requireActiveMember(actor);
        if (!ownerId.equals(actor.id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, resourceName + " no encontrada");
    }

    public void requireSameCouple(UUID resourceCoupleId, User actor) {
        if (resourceCoupleId == null || !resourceCoupleId.equals(requireActiveMember(actor))) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }
}
