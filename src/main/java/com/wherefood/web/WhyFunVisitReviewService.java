package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.User;
import com.wherefood.domain.WhyFunVisit;
import com.wherefood.domain.WhyFunVisitReview;
import com.wherefood.repo.Repositories.WhyFunVisits;
import com.wherefood.repo.Repositories.WhyFunVisitReviews;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Owns authorization and atomic writes for personal reviews of activity visits. */
@Service
@Transactional(readOnly = true)
public class WhyFunVisitReviewService {
    private final WhyFunVisitReviews reviews;
    private final WhyFunVisits visits;
    private final CoupleAuthorizationService authorization;

    public WhyFunVisitReviewService(WhyFunVisitReviews reviews, WhyFunVisits visits, CoupleAuthorizationService authorization) {
        this.reviews = reviews;
        this.visits = visits;
        this.authorization = authorization;
    }

    @Transactional
    public WhyFunVisitReview create(Long visitId, ActivityReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        if (reviews.findByVisitIdAndAuthorId(visitId, actor.id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una reseña de este autor para la visita");
        }
        WhyFunVisitReview review = new WhyFunVisitReview();
        review.visit = visit;
        review.author = review.updatedBy = actor;
        review.createdAt = review.updatedAt = Instant.now();
        apply(review, request);
        return reviews.save(review);
    }

    @Transactional
    public WhyFunVisitReview saveOwn(Long visitId, ActivityReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        WhyFunVisitReview review = reviews.findByVisitIdAndAuthorId(visitId, actor.id).orElseGet(() -> {
            WhyFunVisitReview value = new WhyFunVisitReview();
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
    public WhyFunVisitReview update(Long reviewId, ActivityReviewRequest request, User actor) {
        WhyFunVisitReview review = find(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        review.updatedBy = actor;
        review.updatedAt = java.time.Instant.now();
        review.rating = request.rating();
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
        return reviews.save(review);
    }

    @Transactional
    public void delete(Long reviewId, User actor) {
        WhyFunVisitReview review = find(reviewId);
        authorization.requireReviewAuthor(review.author.id, actor, "Reseña");
        reviews.delete(review);
    }

    private WhyFunVisitReview find(Long reviewId) {
        return reviews.findDetailedByIdAndCoupleId(reviewId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));
    }

    private WhyFunVisit findVisit(Long visitId) {
        return visits.findDetailedByIdAndCoupleId(visitId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada"));
    }

    private static void apply(WhyFunVisitReview review, ActivityReviewRequest request) {
        review.rating = request.rating();
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
    }
}
