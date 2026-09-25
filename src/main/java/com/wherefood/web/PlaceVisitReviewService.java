package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.PlaceVisitReview;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.PlaceVisitReviews;
import com.wherefood.repo.Repositories.PlaceVisits;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns authorization and atomic writes for personal restaurant-visit reviews. */
@Service
@Transactional(readOnly = true)
public class PlaceVisitReviewService {
    private final PlaceVisitReviews reviews;
    private final PlaceVisits visits;
    private final CoupleAuthorizationService authorization;

    public PlaceVisitReviewService(PlaceVisitReviews reviews, PlaceVisits visits,
            CoupleAuthorizationService authorization) {
        this.reviews = reviews;
        this.visits = visits;
        this.authorization = authorization;
    }

    @Transactional
    public PlaceVisitReview create(Long visitId, PlaceVisitReviewRequest request, User actor) {
        var coupleId = authorization.requireActiveMember(actor);
        PlaceVisit visit = findActiveVisit(visitId, coupleId);
        if (reviews.findByVisitIdAndAuthorIdAndCoupleId(visitId, actor.id, coupleId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una reseña de este autor para la visita");
        }
        PlaceVisitReview review = new PlaceVisitReview();
        review.visit = visit;
        review.author = review.updatedBy = actor;
        review.createdAt = review.updatedAt = Instant.now();
        apply(review, request);
        return reviews.save(review);
    }

    @Transactional
    public PlaceVisitReview saveOwn(Long visitId, PlaceVisitReviewRequest request, User actor) {
        var coupleId = authorization.requireActiveMember(actor);
        PlaceVisit visit = findActiveVisit(visitId, coupleId);
        PlaceVisitReview review = reviews.findByVisitIdAndAuthorIdAndCoupleId(visitId, actor.id, coupleId).orElseGet(() -> {
            PlaceVisitReview value = new PlaceVisitReview();
            value.visit = visit;
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
    public PlaceVisitReview update(Long reviewId, PlaceVisitReviewRequest request, User actor) {
        PlaceVisitReview review = findActive(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        review.updatedBy = actor;
        review.updatedAt = Instant.now();
        review.overall = request.overall();
        review.comment = request.comment();
        review.taste = request.taste();
        review.price = request.price();
        return reviews.save(review);
    }

    @Transactional
    public void delete(Long reviewId, User actor) {
        PlaceVisitReview review = findActive(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        reviews.delete(review);
    }

    private PlaceVisitReview findActive(Long reviewId) {
        PlaceVisitReview review = reviews.findDetailedByIdAndCoupleId(reviewId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
        if (review.visit == null || review.visit.place == null || review.visit.place.deactivatedAt != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada");
        }
        return review;
    }

    private PlaceVisit findActiveVisit(Long visitId, java.util.UUID coupleId) {
        PlaceVisit visit = visits.findDetailedByIdAndCoupleId(visitId, coupleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada"));
        if (visit.place == null || visit.place.deactivatedAt != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada");
        }
        return visit;
    }

    private static void apply(PlaceVisitReview review, PlaceVisitReviewRequest request) {
        review.overall = request.overall();
        review.comment = request.comment();
        review.taste = request.taste();
        review.price = request.price();
    }
}
