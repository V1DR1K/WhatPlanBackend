package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Item;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.PlaceVisits;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns item writes and prevents operations on deleted items or archived parent visits. */
@Service
@Transactional(readOnly = true)
public class ItemService {
    private final Items items;
    private final PlaceVisits visits;
    private final CoupleAuthorizationService authorization;

    public ItemService(Items items, PlaceVisits visits, CoupleAuthorizationService authorization) {
        this.items = items;
        this.visits = visits;
        this.authorization = authorization;
    }

    @Transactional
    public Item create(Long visitId, CreateItemRequest request, User actor) {
        authorization.requireActiveMember(actor);
        PlaceVisit visit = visits.findDetailedByIdAndCoupleId(visitId, CoupleContext.current())
                .filter(value -> value.place != null && value.place.deactivatedAt == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Visita no encontrada"));
        Item item = new Item();
        item.visit = visit;
        item.createdBy = actor;
        apply(item, new ItemRequest(request.name()));
        return items.save(item);
    }

    @Transactional
    public Item update(Long itemId, ItemRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Item item = findActive(itemId);
        apply(item, request);
        return items.save(item);
    }

    @Transactional
    public void delete(Long itemId, User actor) {
        authorization.requireActiveMember(actor);
        Item item = findActive(itemId);
        item.deletedAt = Instant.now();
        items.save(item);
    }

    private Item findActive(Long itemId) {
        return items.findByIdAndCoupleId(itemId, CoupleContext.current())
                .filter(value -> value.deletedAt == null && value.visit != null
                        && value.visit.place != null && value.visit.place.deactivatedAt == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ítem no encontrado"));
    }

    private static void apply(Item item, ItemRequest request) {
        item.name = request.name().trim();
        item.updatedAt = Instant.now();
        if (item.createdAt == null) item.createdAt = item.updatedAt;
    }
}
