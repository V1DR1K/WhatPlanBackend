package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Item;
import com.wherefood.domain.ItemReview;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.ItemReviews;
import com.wherefood.repo.Repositories.Items;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Personal review upsert; the author always comes from the authenticated principal. */
@Service
@Transactional(readOnly = true)
public class ItemReviewService {
    private final Items items;
    private final ItemReviews reviews;
    private final CoupleAuthorizationService authorization;

    public ItemReviewService(Items items, ItemReviews reviews, CoupleAuthorizationService authorization) {
        this.items = items;
        this.reviews = reviews;
        this.authorization = authorization;
    }

    @Transactional
    public ItemReview saveOwn(Long itemId, ItemReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Item item = items.findByIdAndCoupleId(itemId, CoupleContext.current())
                .filter(value -> value.deletedAt == null && value.visit != null
                        && value.visit.place != null && value.visit.place.deactivatedAt == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ítem no encontrado"));
        ItemReview review = reviews.findByItemIdAndAuthorId(itemId, actor.id).orElseGet(() -> {
            ItemReview value = new ItemReview();
            value.item = item;
            value.author = actor;
            value.createdAt = Instant.now();
            return value;
        });
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
        review.taste = request.taste();
        review.price = request.price();
        review.updatedAt = Instant.now();
        return reviews.save(review);
    }
}
