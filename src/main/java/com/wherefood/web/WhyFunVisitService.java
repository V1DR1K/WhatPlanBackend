package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.WhyFunVenue;
import com.wherefood.domain.WhyFunVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.WhyFunVenues;
import com.wherefood.repo.Repositories.WhyFunVisits;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns scheduled activity visits and atomically updates the parent activity. */
@Service
@Transactional(readOnly = true)
public class WhyFunVisitService {
    private final WhyFunVenues activities;
    private final WhyFunVisits visits;
    private final CoupleAuthorizationService authorization;

    public WhyFunVisitService(WhyFunVenues activities, WhyFunVisits visits,
            CoupleAuthorizationService authorization) {
        this.activities = activities;
        this.visits = visits;
        this.authorization = authorization;
    }

    @Transactional
    public WhyFunVisit create(Long activityId, ActivityVisitRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVenue activity = findActivity(activityId);
        WhyFunVisit visit = new WhyFunVisit();
        visit.venue = activity;
        visit.scheduledAt = request.scheduledAt();
        visit.createdBy = visit.updatedBy = actor;
        visit.createdAt = visit.updatedAt = Instant.now();
        touch(activity, actor);
        return visits.save(visit);
    }

    @Transactional
    public WhyFunVisit update(Long visitId, ActivityVisitRequest request, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        visit.scheduledAt = request.scheduledAt();
        visit.updatedBy = actor;
        visit.updatedAt = Instant.now();
        touch(visit.venue, actor);
        return visits.save(visit);
    }

    @Transactional
    public void delete(Long visitId, User actor) {
        authorization.requireActiveMember(actor);
        WhyFunVisit visit = findVisit(visitId);
        visits.delete(visit);
        touch(visit.venue, actor);
    }

    private WhyFunVenue findActivity(Long activityId) {
        return activities.findDetailedByIdAndCoupleId(activityId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Actividad no encontrada"));
    }

    private WhyFunVisit findVisit(Long visitId) {
        return visits.findDetailedByIdAndCoupleId(visitId, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Visita no encontrada"));
    }

    private void touch(WhyFunVenue activity, User actor) {
        activity.updatedBy = actor;
        activity.updatedAt = Instant.now();
        activities.save(activity);
    }
}
