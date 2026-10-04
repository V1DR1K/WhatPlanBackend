package com.wherefood.repo;

import com.wherefood.domain.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public final class JourneyRepositories {
    private JourneyRepositories() {}

    @org.springframework.data.repository.NoRepositoryBean
    public interface Scoped<T extends JourneyEntity>
            extends org.springframework.data.repository.Repository<T, UUID> {
        <S extends T> S save(S entity);

        <S extends T> S saveAndFlush(S entity);

        void flush();

        void delete(T entity);

        void deleteAll(Iterable<? extends T> entities);

        Optional<T> findByIdAndCoupleId(UUID id, UUID coupleId);
    }

    public interface Journeys extends Scoped<Journey> {
        List<Journey> findByCoupleIdAndArchivedFalseOrderByStartsOnDescIdDesc(UUID coupleId);

        List<Journey> findByCoupleIdOrderByStartsOnDescIdDesc(UUID coupleId, Pageable pageable);
    }

    public interface Stages extends Scoped<JourneyStage> {
        List<JourneyStage> findByCoupleIdAndJourneyIdIn(UUID coupleId, List<UUID> journeyIds);

        List<JourneyStage> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface Points extends Scoped<JourneyPoint> {
        List<JourneyPoint> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByCoupleIdAndCategory(UUID coupleId, String category);
    }

    public interface Stays extends Scoped<JourneyStay> {
        List<JourneyStay> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface PackingItems extends Scoped<JourneyPackingItem> {
        <S extends JourneyPackingItem> List<S> saveAll(Iterable<S> entities);

        List<JourneyPackingItem> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        List<JourneyPackingItem> findByJourneyIdAndCoupleIdAndUserIdOrderByPositionAscIdAsc(
                UUID journeyId, UUID coupleId, Long userId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface Movements extends Scoped<JourneyMovement> {
        List<JourneyMovement> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface FileSummary {
        UUID getId();

        String getName();

        String getContentType();

        long getByteSize();

        UUID getStageId();

        UUID getPointId();

        UUID getStayId();

        UUID getMovementId();

        String getPurpose();

        java.time.LocalDate getDay();

        Integer getWidth();

        Integer getHeight();
    }

    public interface Files extends Scoped<JourneyFile> {
        List<FileSummary> findSummariesByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        long countByJourneyIdAndCoupleIdAndPurpose(UUID journeyId, UUID coupleId, String purpose);

        long countByJourneyIdAndCoupleIdAndPurposeAndDay(UUID journeyId, UUID coupleId, String purpose, java.time.LocalDate day);

        List<JourneyFile> findByJourneyIdAndCoupleIdAndPurposeAndDayOrderByCreatedAtAscIdAsc(UUID journeyId, UUID coupleId, String purpose, java.time.LocalDate day);

        List<JourneyFile> findByJourneyIdAndCoupleIdAndPurposeOrderByCreatedAtAscIdAsc(UUID journeyId, UUID coupleId, String purpose);
    }

    public interface Days extends Scoped<JourneyDay> {
        Optional<JourneyDay> findByJourneyIdAndCoupleIdAndDay(UUID journeyId, UUID coupleId, java.time.LocalDate day);

        List<JourneyDay> findByJourneyIdAndCoupleIdOrderByDay(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface DayReviews extends Scoped<JourneyDayReview> {
        List<JourneyDayReview> findByJourneyIdAndCoupleIdAndDayOrderByUpdatedAtAsc(UUID journeyId, UUID coupleId, java.time.LocalDate day);

        Optional<JourneyDayReview> findByJourneyIdAndCoupleIdAndDayAndUserId(UUID journeyId, UUID coupleId, java.time.LocalDate day, Long userId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }

    public interface Reviews extends Scoped<JourneyReview> {
        List<JourneyReview> findByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);

        boolean existsByJourneyIdAndCoupleId(UUID journeyId, UUID coupleId);
    }
}
