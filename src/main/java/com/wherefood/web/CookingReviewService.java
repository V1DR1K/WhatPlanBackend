package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.CookingReview;
import com.wherefood.domain.Cooking;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Cookings;
import com.wherefood.repo.Repositories.CookingReviews;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns authorization and atomic writes for personal cooking reviews. */
@Service
@Transactional(readOnly = true)
public class CookingReviewService {
    private final CookingReviews reviews;
    private final Cookings cookings;
    private final CoupleAuthorizationService authorization;

    public CookingReviewService(CookingReviews reviews, Cookings cookings, CoupleAuthorizationService authorization) {
        this.reviews = reviews;
        this.cookings = cookings;
        this.authorization = authorization;
    }

    @Transactional
    public CookingReview create(Long cookingId, CookingReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Cooking cooking = findCooking(cookingId);
        if (reviews.findByCookingIdAndAuthorId(cookingId, actor.id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una reseña de este autor para la preparación");
        }
        CookingReview review = new CookingReview();
        review.cooking = cooking;
        review.author = review.updatedBy = actor;
        review.createdAt = review.updatedAt = Instant.now();
        apply(review, request);
        return reviews.save(review);
    }

    @Transactional
    public CookingReview saveOwn(Long cookingId, CookingReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Cooking cooking = findCooking(cookingId);
        CookingReview review = reviews.findByCookingIdAndAuthorId(cookingId, actor.id).orElseGet(() -> {
            CookingReview value = new CookingReview();
            value.cooking = cooking;
            value.author = actor;
            value.createdAt = Instant.now();
            return value;
        });
        review.updatedBy = actor;
        review.updatedAt = Instant.now();
        apply(review, request);
        return reviews.save(review);
    }

    @Transactional
    public CookingReview update(Long reviewId, CookingReviewRequest request, User actor) {
        CookingReview review = find(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        review.updatedBy = actor;
        review.updatedAt = java.time.Instant.now();
        review.rating = request.rating();
        review.complexity = request.complexity();
        review.taste = request.taste();
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
        return reviews.save(review);
    }

    @Transactional
    public void delete(Long reviewId, User actor) {
        CookingReview review = find(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        reviews.delete(review);
    }

    private CookingReview find(Long reviewId) {
        return reviews.findDetailedByIdAndCoupleId(reviewId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
    }

    private Cooking findCooking(Long cookingId) {
        return cookings.findDetailedByIdAndCoupleId(cookingId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preparación no encontrada"));
    }

    private static void apply(CookingReview review, CookingReviewRequest request) {
        review.rating = request.rating();
        review.complexity = request.complexity();
        review.taste = request.taste();
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
    }
}
