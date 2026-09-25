package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceReview;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.PlaceReviews;
import com.wherefood.repo.Repositories.Places;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Personal place review upsert guarded by active membership and place visibility. */
@Service
@Transactional(readOnly = true)
public class PlaceReviewService {
    private final Places places;
    private final PlaceReviews reviews;
    private final CoupleAuthorizationService authorization;

    public PlaceReviewService(Places places, PlaceReviews reviews,
            CoupleAuthorizationService authorization) {
        this.places = places;
        this.reviews = reviews;
        this.authorization = authorization;
    }

    @Transactional
    public PlaceReview saveOwn(Long placeId, PlaceReviewRequest request, User actor) {
        authorization.requireActiveMember(actor);
        Place place = places.findByIdAndCoupleId(placeId, CoupleContext.current())
                .filter(value -> value.deactivatedAt == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lugar no encontrado"));
        if (Stream.of(request.location(), request.heating(), request.bathrooms(), request.exterior(),
                request.seating(), request.service(), request.ambiance()).allMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Calificá al menos un aspecto del lugar");
        }
        PlaceReview review = reviews.findByPlaceIdAndAuthorIdAndCoupleId(placeId, actor.id, CoupleContext.current()).orElseGet(() -> {
            PlaceReview value = new PlaceReview();
            value.place = place;
            value.author = actor;
            value.createdAt = Instant.now();
            return value;
        });
        review.comment = request.comment() == null || request.comment().isBlank() ? null : request.comment();
        review.location = request.location();
        review.heating = request.heating();
        review.bathrooms = request.bathrooms();
        review.exterior = request.exterior();
        review.seating = request.seating();
        review.service = request.service();
        review.ambiance = request.ambiance();
        review.updatedAt = Instant.now();
        return reviews.save(review);
    }
}
