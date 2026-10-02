package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.SpecialDates;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Couple-scoped operations for the special-date aggregate. */
@Service
@Transactional(readOnly = true)
public class SpecialDateService {
    private final SpecialDates dates;
    private final CoupleAuthorizationService authorization;

    public SpecialDateService(SpecialDates dates, CoupleAuthorizationService authorization) {
        this.dates = dates;
        this.authorization = authorization;
    }

    public List<SpecialDate> list(User actor) {
        authorization.requireActiveMember(actor);
        return dates.findAllByCoupleIdOrderByDateAscLabelAscIdAsc(CoupleContext.current());
    }

    @Transactional
    public SpecialDate create(SpecialDateRequest request, User actor) {
        authorization.requireActiveMember(actor);
        SpecialDate value = new SpecialDate();
        apply(value, request);
        value.createdAt = value.updatedAt = Instant.now();
        return dates.save(value);
    }

    @Transactional
    public SpecialDate update(Long id, SpecialDateRequest request, User actor) {
        authorization.requireActiveMember(actor);
        SpecialDate value = find(id);
        apply(value, request);
        value.updatedAt = Instant.now();
        return dates.save(value);
    }

    @Transactional
    public void delete(Long id, User actor) {
        authorization.requireActiveMember(actor);
        dates.delete(find(id));
    }

    private SpecialDate find(Long id) {
        return dates.findByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fecha especial no encontrada"));
    }

    private static void apply(SpecialDate value, SpecialDateRequest request) {
        value.date = request.date();
        value.label = request.label().trim();
        value.recurrence = request.recurrence();
    }
}
