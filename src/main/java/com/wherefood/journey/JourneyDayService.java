package com.wherefood.journey;

import static com.wherefood.journey.JourneyDtos.*;
import static com.wherefood.repo.JourneyRepositories.*;

import com.wherefood.config.CoupleContext;
import com.wherefood.domain.*;
import com.wherefood.repo.JourneyRepositories.Days;
import com.wherefood.repo.JourneyRepositories.DayReviews;
import com.wherefood.repo.JourneyRepositories.Files;
import com.wherefood.repo.JourneyRepositories.Journeys;
import com.wherefood.repo.Repositories.SpecialDates;
import com.wherefood.web.PhotoStorage;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class JourneyDayService {
    private final Journeys trips;
    private final Days days;
    private final DayReviews dayReviews;
    private final Files files;
    private final com.wherefood.repo.JourneyRepositories.Stages stages;
    private final SpecialDates specialDates;
    private final LocationService locations;
    private final JourneySourceRepository sources;
    private final PhotoStorage photoStorage;
    private final long maxUploadBytes;

    public JourneyDayService(Journeys trips, Days days, DayReviews dayReviews, Files files,
            com.wherefood.repo.JourneyRepositories.Stages stages,
            SpecialDates specialDates, LocationService locations,
            JourneySourceRepository sources,
            PhotoStorage photoStorage,
            @org.springframework.beans.factory.annotation.Value("${app.journey.max-upload-bytes:10485760}")
            long maxUploadBytes) {
        this.trips = trips;
        this.days = days;
        this.dayReviews = dayReviews;
        this.files = files;
        this.stages = stages;
        this.specialDates = specialDates;
        this.locations = locations;
        this.sources = sources;
        this.photoStorage = photoStorage;
        this.maxUploadBytes = maxUploadBytes;
    }

    private UUID couple() {
        return CoupleContext.current();
    }

    private Journey trip(UUID id, boolean edit) {
        Journey value = trips.findByIdAndCoupleId(id, couple())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Viaje no encontrado"));
        if (edit && value.archived)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El viaje está archivado");
        return value;
    }

    private JourneyDay getOrCreateDay(Journey journey, LocalDate day) {
        return days.findByJourneyIdAndCoupleIdAndDay(journey.id, couple(), day)
                .orElseGet(() -> {
                    JourneyDay value = new JourneyDay();
                    value.journeyId = journey.id;
                    value.day = day;
                    return days.save(value);
                });
    }

    private JourneyDay within(Journey journey, LocalDate date) {
        if (date == null || date.isBefore(journey.startsOn) || date.isAfter(journey.endsOn))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí un día dentro del viaje");
        return days.findByJourneyIdAndCoupleIdAndDay(journey.id, couple(), date).orElse(null);
    }

    public JourneyDayDto day(UUID journeyId, LocalDate date) {
        Journey journey = trip(journeyId, false);
        JourneyDay stored = within(journey, date);
        List<JourneyStage> stages = journeyStages(journeyId);
        List<JourneySpecialDateDto> dateLinks = specialDates
                .findAllByCoupleIdOrderByDateAscLabelAscIdAsc(couple()).stream()
                .filter(template -> stages.stream().anyMatch(stage -> stage.position == 0
                        && !date.isBefore(stage.startsOn) && !date.isAfter(stage.endsOn)))
                .filter(template -> matches(template, date))
                .map(template -> new JourneySpecialDateDto(template.id, template.label,
                        template.recurrence == null ? "ONCE" : template.recurrence.name(),
                        "/app/when-dates/" + template.id + "/" + date))
                .toList();
        List<JourneyPhotoDto> dayPhotos = files
                .findByJourneyIdAndCoupleIdAndPurposeAndDayOrderByCreatedAtAscIdAsc(
                        journeyId, couple(), "DAY", date).stream().map(this::photoDto).toList();
        List<JourneyDayReviewDto> reviews = stored == null ? List.of() : dayReviews
                .findByJourneyIdAndCoupleIdAndDayOrderByUpdatedAtAsc(journeyId, couple(), date)
                .stream().map(this::reviewDto).toList();
        return new JourneyDayDto(date, stored == null ? null : stored.story,
                sources.journeyDayEntries(journeyId, date), dateLinks, dayPhotos, reviews);
    }

    public List<JourneyDayIndexDto> days(UUID journeyId) {
        Journey journey = trip(journeyId, false);
        String destination = journeyStages(journeyId).stream()
                .filter(stage -> stage.position == 0).findFirst()
                .map(stage -> locations.city(stage.cityId).name()).orElse("");
        List<JourneyDayIndexDto> result = new ArrayList<>();
        for (LocalDate date = journey.startsOn; !date.isAfter(journey.endsOn); date = date.plusDays(1)) {
            result.add(new JourneyDayIndexDto(date, destination.isBlank() ? List.of() : List.of(destination)));
        }
        return result;
    }

    public List<JourneyGalleryEntryDto> gallery(UUID journeyId) {
        Journey journey = trip(journeyId, false);
        return sources.journeyGalleryEntries(journeyId, journey.startsOn, journey.endsOn);
    }

    @Transactional
    public JourneyDayDto saveStory(UUID journeyId, LocalDate date, JourneyDayStoryRequest request) {
        Journey journey = trip(journeyId, true);
        within(journey, date);
        JourneyDay value = getOrCreateDay(journey, date);
        value.story = request.story() == null || request.story().isBlank()
                ? null : request.story().trim();
        days.save(value);
        if (value.story == null) pruneEmptyDay(journeyId, date);
        return day(journeyId, date);
    }

    @Transactional
    public JourneyDayReviewDto saveReview(UUID journeyId, LocalDate date,
            JourneyDayReviewRequest request, User actor) {
        Journey journey = trip(journeyId, true);
        within(journey, date);
        String comment = request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim();
        if (request.rating() == null && comment == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Agregá una puntuación, un comentario o ambos");
        JourneyDayReview review = dayReviews
                .findByJourneyIdAndCoupleIdAndDayAndUserId(journeyId, couple(), date, actor.id)
                .orElseGet(JourneyDayReview::new);
        getOrCreateDay(journey, date);
        review.journeyId = journeyId;
        review.day = date;
        review.userId = actor.id;
        review.memberId = sources.memberId(actor.id);
        review.rating = request.rating();
        review.comment = comment;
        review.updatedAt = Instant.now();
        dayReviews.save(review);
        return reviewDto(review);
    }

    @Transactional
    public void deleteReview(UUID journeyId, LocalDate date, User actor) {
        Journey journey = trip(journeyId, true);
        within(journey, date);
        JourneyDayReview review = dayReviews
                .findByJourneyIdAndCoupleIdAndDayAndUserId(journeyId, couple(), date, actor.id)
                .orElse(null);
        if (review != null) {
            dayReviews.delete(review);
            pruneEmptyDay(journeyId, date);
        }
    }

    @Transactional
    public JourneyPhotoDto uploadPhoto(UUID journeyId, String purpose, LocalDate date,
            MultipartFile upload) throws IOException {
        Journey journey = trip(journeyId, true);
        boolean dayPhoto = "DAY".equals(purpose);
        if (!dayPhoto && !"TRIP".equals(purpose))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de foto inválido");
        if (dayPhoto) {
            within(journey, date);
        } else if (date != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las fotos generales no llevan fecha");
        }
        if (upload == null || upload.isEmpty() || upload.getSize() > maxUploadBytes)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "La foto está vacía o supera el límite permitido");
        long limit = dayPhoto ? journey.maxDayPhotos : journey.maxTripPhotos;
        long current = dayPhoto
                ? files.countByJourneyIdAndCoupleIdAndPurposeAndDay(journeyId, couple(), purpose, date)
                : files.countByJourneyIdAndCoupleIdAndPurpose(journeyId, couple(), purpose);
        if (current >= limit)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El viaje alcanzó el límite de fotos configurado");
        PhotoStorage.ImageData image = photoStorage.processJourneyPhoto(upload);
        if (dayPhoto) getOrCreateDay(journey, date);
        String rawName = Optional.ofNullable(upload.getOriginalFilename()).orElse("foto.webp")
                .replace('\\', '/');
        String name = rawName.substring(rawName.lastIndexOf('/') + 1)
                .replace('\r', '_').replace('\n', '_');
        JourneyFile file = new JourneyFile();
        file.journeyId = journeyId;
        file.purpose = purpose;
        file.day = date;
        file.name = name.isBlank() ? "foto.webp" : name.substring(0, Math.min(name.length(), 255));
        file.contentType = "image/webp";
        file.content = Base64.getDecoder().decode(image.image());
        file.thumbnailContent = Base64.getDecoder().decode(image.thumbnail());
        file.byteSize = file.content.length;
        file.width = image.width();
        file.height = image.height();
        file.createdAt = Instant.now();
        JourneyFile saved = files.saveAndFlush(file);
        if (journey.coverFileId == null) {
            journey.coverFileId = saved.id;
            trips.save(journey);
        }
        return photoDto(saved);
    }

    @Transactional
    public UUID setCover(UUID journeyId, UUID fileId) {
        Journey journey = trip(journeyId, true);
        JourneyFile file = files.findByIdAndCoupleId(fileId, couple())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Foto no encontrada"));
        if (!file.journeyId.equals(journeyId)
                || !("TRIP".equals(file.purpose) || "DAY".equals(file.purpose)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una foto de este viaje o de sus días");
        journey.coverFileId = fileId;
        trips.save(journey);
        return fileId;
    }

    @Transactional
    public void clearDeletedCover(UUID journeyId, UUID fileId) {
        trips.findByIdAndCoupleId(journeyId, couple()).filter(t -> fileId.equals(t.coverFileId))
                .ifPresent(t -> { t.coverFileId = null; trips.save(t); });
    }

    public byte[] photoBytes(UUID id, boolean thumbnail) {
        JourneyFile file = files.findByIdAndCoupleId(id, couple())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Foto no encontrada"));
        if (!"TRIP".equals(file.purpose) && !"DAY".equals(file.purpose))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada");
        return thumbnail && file.thumbnailContent != null ? file.thumbnailContent : file.content;
    }

    private JourneyPhotoDto photoDto(JourneyFile value) {
        String base = "/whither-journey/files/" + value.id + "/content";
        return new JourneyPhotoDto(value.id, value.name, base, base + "?thumbnail=true",
                value.width == null ? 0 : value.width,
                value.height == null ? 0 : value.height, value.purpose, value.day);
    }

    private JourneyDayReviewDto reviewDto(JourneyDayReview value) {
        String author = sources.members().stream().filter(m -> m.id().equals(value.userId))
                .map(MemberDto::username).findFirst().orElse("Integrante anterior");
        return new JourneyDayReviewDto(value.id, value.userId, author, value.rating, value.comment);
    }

    private List<JourneyStage> journeyStages(UUID journeyId) {
        return stages.findByJourneyIdAndCoupleId(journeyId, couple());
    }

    private static boolean matches(SpecialDate template, LocalDate date) {
        return SpecialDateOccurrenceWindow.forDate(template, date).isPresent();
    }

    private void pruneEmptyDay(UUID journeyId, LocalDate date) {
        days.findByJourneyIdAndCoupleIdAndDay(journeyId, couple(), date).ifPresent(value -> {
            boolean hasPhoto = files.countByJourneyIdAndCoupleIdAndPurposeAndDay(
                    journeyId, couple(), "DAY", date) > 0;
            boolean hasReview = !dayReviews.findByJourneyIdAndCoupleIdAndDayOrderByUpdatedAtAsc(
                    journeyId, couple(), date).isEmpty();
            if (value.story == null && !hasPhoto && !hasReview) days.delete(value);
        });
    }
}
