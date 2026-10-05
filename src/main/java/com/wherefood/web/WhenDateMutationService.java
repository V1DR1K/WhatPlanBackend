package com.wherefood.web;

import com.wherefood.config.CoupleContext;
import com.wherefood.couple.CoupleAuthorizationService;
import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateOccurrence;
import com.wherefood.domain.SpecialDateOccurrenceComment;
import com.wherefood.domain.SpecialDateOccurrencePhoto;
import com.wherefood.domain.SpecialDateOccurrenceWindow;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.SpecialDateOccurrenceComments;
import com.wherefood.repo.Repositories.SpecialDateOccurrencePhotos;
import com.wherefood.repo.Repositories.SpecialDateOccurrences;
import com.wherefood.repo.Repositories.SpecialDates;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Authorization and atomic mutations for a couple's calendar memories. */
@Service
@Transactional(readOnly = true)
public class WhenDateMutationService {
    private static final int MAX_PHOTOS = 4;

    private final SpecialDates specialDates;
    private final SpecialDateOccurrences occurrences;
    private final SpecialDateOccurrenceComments comments;
    private final SpecialDateOccurrencePhotos photos;
    private final PhotoStorage storage;
    private final CoupleAuthorizationService authorization;
    private final com.wherefood.journey.JourneyService journey;

    public WhenDateMutationService(SpecialDates specialDates, SpecialDateOccurrences occurrences,
            SpecialDateOccurrenceComments comments, SpecialDateOccurrencePhotos photos,
            PhotoStorage storage, CoupleAuthorizationService authorization) { this(specialDates, occurrences, comments, photos, storage, authorization, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public WhenDateMutationService(SpecialDates specialDates, SpecialDateOccurrences occurrences,
            SpecialDateOccurrenceComments comments, SpecialDateOccurrencePhotos photos,
            PhotoStorage storage, CoupleAuthorizationService authorization, com.wherefood.journey.JourneyService journey) {
        this.specialDates = specialDates;
        this.occurrences = occurrences;
        this.comments = comments;
        this.photos = photos;
        this.storage = storage;
        this.authorization = authorization;
        this.journey = journey;
    }

    @Transactional
    public SpecialDateOccurrence saveComment(Long specialDateId, LocalDate date,
            WhenDateCommentRequest request, User actor) {
        requireMember(actor);
        SpecialDateOccurrence occurrence = ensureOccurrence(findDate(specialDateId), date, actor);
        SpecialDateOccurrenceComment comment = comments.findByOccurrenceIdAndAuthorIdAndCoupleId(occurrence.id, actor.id, CoupleContext.current())
                .orElseGet(() -> {
                    SpecialDateOccurrenceComment value = new SpecialDateOccurrenceComment();
                    value.occurrence = occurrence;
                    value.author = actor;
                    value.createdAt = Instant.now();
                    return value;
                });
        comment.comment = request.comment().trim();
        comment.updatedBy = actor;
        comment.updatedAt = Instant.now();
        comments.save(comment);
        touch(occurrence, actor);
        return occurrence;
    }

    @Transactional
    public void deleteComment(Long specialDateId, LocalDate date, User actor) {
        requireMember(actor);
        SpecialDateOccurrence occurrence = findOccurrenceOn(specialDateId, date)
                .orElseThrow(() -> notFound("Recuerdo"));
        comments.findByOccurrenceIdAndAuthorIdAndCoupleId(occurrence.id, actor.id, CoupleContext.current()).ifPresent(comments::delete);
        touch(occurrence, actor);
    }

    @Transactional
    public SpecialDateOccurrence uploadPhoto(Long specialDateId, LocalDate date,
            MultipartFile file, User actor) throws IOException {
        requireMember(actor);
        SpecialDateOccurrence occurrence = ensureOccurrence(findDate(specialDateId), date, actor);
        List<SpecialDateOccurrencePhoto> current = photos.findByOccurrenceIdAndCoupleIdOrderByPositionAscIdAsc(
                occurrence.id, CoupleContext.current());
        if (current.size() >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta fecha admite hasta " + MAX_PHOTOS + " fotos");
        }
        int position = current.isEmpty() ? 0 : current.getLast().position + 1;
        SpecialDateOccurrencePhoto photo = photos.save(storage.store(occurrence, actor, position, file));
        if (occurrence.coverPhotoId == null) occurrence.coverPhotoId = photo.id;
        touch(occurrence, actor);
        return occurrence;
    }

    @Transactional
    public SpecialDateOccurrence setCover(Long occurrenceId, Long photoId, User actor) {
        requireMember(actor);
        SpecialDateOccurrence occurrence = findOccurrence(occurrenceId);
        SpecialDateOccurrencePhoto photo = photos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        if (!photo.occurrence.id.equals(occurrence.id)) throw notFound("Foto");
        occurrence.coverPhotoId = photo.id;
        touch(occurrence, actor);
        return occurrence;
    }

    @Transactional
    public void deletePhoto(Long photoId, User actor) {
        requireMember(actor);
        SpecialDateOccurrencePhoto photo = photos.findDetailedByIdAndCoupleId(photoId, CoupleContext.current())
                .orElseThrow(() -> notFound("Foto"));
        SpecialDateOccurrence occurrence = findOccurrence(photo.occurrence.id);
        boolean wasCover = photo.id.equals(occurrence.coverPhotoId);
        photos.delete(photo);
        photos.flush();
        if (wasCover) {
            occurrence.coverPhotoId = photos.findByOccurrenceIdAndCoupleIdOrderByPositionAscIdAsc(
                    occurrence.id, CoupleContext.current())
                    .stream().findFirst().map(value -> value.id).orElse(null);
        }
        touch(occurrence, actor);
    }

    private SpecialDateOccurrence ensureOccurrence(SpecialDate date, LocalDate occurredOn, User actor) {return ensureOccurrence(date,occurredOn,actor,false);}

    private SpecialDateOccurrence ensureOccurrence(SpecialDate date, LocalDate occurredOn, User actor, boolean allowFuture) {
        SpecialDate lockedDate = specialDates.findLockedByIdAndCoupleId(date.id, CoupleContext.current())
                .orElseThrow(() -> notFound("Fecha especial"));
        var existingRange = occurrences
                .findDetailedBySpecialDateIdAndOccurredOnLessThanEqualAndEndsOnGreaterThanEqualAndCoupleId(
                        lockedDate.id, occurredOn, occurredOn, CoupleContext.current());
        if (existingRange.isPresent()) {
            if (!allowFuture && occurredOn.isAfter(RosarioClock.today()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La fecha todavía no ocurrió");
            return existingRange.get();
        }
        validateOccurrence(lockedDate, occurredOn, allowFuture);
        return occurrences.findDetailedBySpecialDateIdAndOccurredOnAndCoupleId(lockedDate.id, occurredOn, CoupleContext.current())
                .orElseGet(() -> {
                    SpecialDateOccurrence value = new SpecialDateOccurrence();
                    value.specialDate = lockedDate;
                    SpecialDateOccurrenceWindow.Window window = SpecialDateOccurrenceWindow
                            .forDate(lockedDate, occurredOn).orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "La fecha no coincide con esta fecha especial"));
                    value.occurredOn = window.startsOn();
                    value.endsOn = window.endsOn();
                    value.cityId = 1L;
                    if (journey != null) journey.locateNew(value, null, null, null, value.occurredOn);
                    value.createdBy = value.updatedBy = actor;
                    value.createdAt = value.updatedAt = Instant.now();
                    return occurrences.save(value);
                });
    }

    @Transactional
    public SpecialDateOccurrence saveLocation(Long specialDateId, LocalDate date, com.wherefood.journey.JourneyDtos.BindingRequest request, User actor) {
        requireMember(actor);
        SpecialDateOccurrence occurrence = ensureOccurrence(findDate(specialDateId), date, actor, true);
        if (journey != null) journey.locateNew(occurrence, occurrence.cityId, request.cityId(), request.stageId(), date);
        touch(occurrence, actor);
        return occurrence;
    }

    private SpecialDate findDate(Long id) {
        return specialDates.findByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> notFound("Fecha especial"));
    }

    private SpecialDateOccurrence findOccurrence(Long id) {
        return occurrences.findDetailedByIdAndCoupleId(id, CoupleContext.current())
                .orElseThrow(() -> notFound("Recuerdo"));
    }

    private java.util.Optional<SpecialDateOccurrence> findOccurrenceOn(Long specialDateId, LocalDate date) {
        return occurrences.findDetailedBySpecialDateIdAndOccurredOnLessThanEqualAndEndsOnGreaterThanEqualAndCoupleId(
                specialDateId, date, date, CoupleContext.current());
    }

    private void requireMember(User actor) { authorization.requireActiveMember(actor); }

    private void touch(SpecialDateOccurrence occurrence, User actor) {
        occurrence.updatedBy = actor;
        occurrence.updatedAt = Instant.now();
        occurrences.save(occurrence);
    }

    private static void validateOccurrence(SpecialDate date, LocalDate occurredOn, boolean allowFuture) {
        if (!allowFuture && occurredOn.isAfter(RosarioClock.today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La fecha todavía no ocurrió");
        if (SpecialDateOccurrenceWindow.forDate(date, occurredOn).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La fecha no coincide con esta fecha especial");
    }

    private static ResponseStatusException notFound(String type) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrado");
    }
}
