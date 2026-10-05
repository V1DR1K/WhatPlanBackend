package com.wherefood.repo;

import com.wherefood.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public final class Repositories {
 private Repositories() {}

 public interface WhenDateSummaryProjection {
  Long getSpecialDateId();
  String getLabel();
  String getRecurrence();
  LocalDate getOccurredOn();
  LocalDate getEndsOn();
  Long getExperienceCount();
  String getImageUrl();
 }

 public interface InvitationLocator {
  Long getId();
  java.util.UUID getCoupleId();
 }

 public interface Users extends JpaRepository<User, Long> {
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select u from User u where u.id = :id")
   Optional<User> findLockedById(@Param("id") Long id);
   @Query("select u from User u where lower(u.username) = lower(:username)")
   Optional<User> findByUsernameIgnoreCase(@Param("username") String username);
   Optional<User> findByAuthUserId(java.util.UUID authUserId);
   @Modifying
   @Query("update User u set u.defaultZoneId = null where u.defaultZoneId = :zoneId")
   int clearDefaultZone(@Param("zoneId") Long zoneId);
 }

 public interface Couples extends JpaRepository<Couple, java.util.UUID> {
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select c from Couple c where c.id = :id")
   Optional<Couple> findLockedById(@Param("id") java.util.UUID id);
 }

 public interface CoupleMembers extends JpaRepository<CoupleMember, Long> {
   @Query("select m.couple.id from CoupleMember m where m.user.id = :userId and m.status = com.wherefood.domain.CoupleMemberStatus.ACTIVE")
   Optional<java.util.UUID> findActiveCoupleIdByUserId(@Param("userId") Long userId);

   @Query("select m from CoupleMember m join fetch m.user where m.couple.id = :coupleId and m.status = :status order by m.slot")
   List<CoupleMember> findByCoupleIdAndStatusOrderBySlot(@Param("coupleId") java.util.UUID coupleId,
           @Param("status") CoupleMemberStatus status);

   @EntityGraph(attributePaths = {"couple", "user"})
   Optional<CoupleMember> findByCoupleIdAndUserIdAndStatus(java.util.UUID coupleId, Long userId, CoupleMemberStatus status);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select m from CoupleMember m where m.couple.id = :coupleId and m.user.id = :userId and m.status = :status")
   Optional<CoupleMember> findLockedByCoupleIdAndUserIdAndStatus(@Param("coupleId") java.util.UUID coupleId,
           @Param("userId") Long userId, @Param("status") CoupleMemberStatus status);

   long countByCoupleIdAndStatus(java.util.UUID coupleId, CoupleMemberStatus status);
 }

 public interface CoupleInvitations extends JpaRepository<CoupleInvitation, Long> {
   @Query("select i.id as id, i.couple.id as coupleId from CoupleInvitation i where i.tokenHash = :tokenHash")
   Optional<InvitationLocator> findInvitationLocatorByTokenHash(@Param("tokenHash") String tokenHash);

   @EntityGraph(attributePaths = {"couple", "createdBy"})
   Optional<CoupleInvitation> findByTokenHash(String tokenHash);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @EntityGraph(attributePaths = {"couple", "createdBy"})
   Optional<CoupleInvitation> findLockedById(Long id);

   @EntityGraph(attributePaths = {"couple"})
   List<CoupleInvitation> findByCoupleIdAndStatusOrderByCreatedAtDesc(java.util.UUID coupleId, CoupleInvitationStatus status);

   Optional<CoupleInvitation> findByIdAndCoupleId(Long id, java.util.UUID coupleId);

   long countByCoupleIdAndCreatedAtAfter(java.util.UUID coupleId, java.time.Instant since);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select i from CoupleInvitation i where i.id = :id and i.couple.id = :coupleId")
   Optional<CoupleInvitation> findLockedByIdAndCoupleId(@Param("id") Long id,
           @Param("coupleId") java.util.UUID coupleId);
 }

 public interface Categories extends JpaRepository<Category, Long> {
  List<Category> findByActiveTrueOrderByName();
 }
  public interface HighlightTags extends JpaRepository<HighlightTag, Long> {
    List<HighlightTag> findByActiveTrueOrderByNameAsc();
    List<HighlightTag> findAllByOrderByNameAsc();
  }
  public interface SpecialDates extends CoupleScopedRepository<SpecialDate> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select specialDate from SpecialDate specialDate where specialDate.id = :id and specialDate.coupleId = :coupleId")
    Optional<SpecialDate> findLockedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
    List<SpecialDate> findAllByCoupleIdOrderByDateAscLabelAscIdAsc(java.util.UUID coupleId);

    @Query(value = """
      WITH experience_events AS (
        SELECT v.couple_id, v.visited_on AS occurred_on, 'FOOD' AS section, v.id AS experience_id,
          CASE WHEN v.cover_photo_id IS NOT NULL THEN '/place-visit-photos/' || v.cover_photo_id || '?thumbnail=true'
               WHEN EXISTS (SELECT 1 FROM place_photos pp WHERE pp.place_id = p.id AND pp.couple_id = v.couple_id)
               THEN '/places/' || p.id || '/photo?thumbnail=true' END AS image_url
        FROM place_visits v JOIN places p ON p.id = v.place_id AND p.couple_id = v.couple_id
        WHERE v.couple_id = :coupleId AND v.visited_on <= :today
          AND (CAST(:zoneId AS bigint) IS NULL OR v.city_id = CAST(:zoneId AS bigint))
        UNION ALL
        SELECT v.couple_id, v.watched_on, 'FILM', v.id,
          CASE WHEN EXISTS (SELECT 1 FROM film_photos fp WHERE fp.film_id = f.id AND fp.couple_id = v.couple_id)
               THEN '/films/' || f.id || '/photo?thumbnail=true' ELSE f.poster_path END
        FROM film_views v JOIN films f ON f.id = v.film_id AND f.couple_id = v.couple_id
        WHERE v.couple_id = :coupleId AND v.watched_on <= :today
        UNION ALL
        SELECT c.couple_id, c.cooked_on, 'COOK', c.id,
          CASE WHEN EXISTS (SELECT 1 FROM recipe_photos rp WHERE rp.recipe_id = r.id AND rp.couple_id = c.couple_id)
               THEN '/how-cook/recipes/' || r.id || '/photo?thumbnail=true' END
        FROM cookings c JOIN recipes r ON r.id = c.recipe_id AND r.couple_id = c.couple_id
        WHERE c.couple_id = :coupleId AND c.cooked_on <= :today
        UNION ALL
        SELECT v.couple_id, v.scheduled_at, 'FUN', v.id,
          CASE WHEN v.cover_photo_id IS NOT NULL THEN '/why-fun/activity-visit-photos/' || v.cover_photo_id || '?thumbnail=true'
               WHEN EXISTS (SELECT 1 FROM why_fun_venue_photos vp WHERE vp.venue_id = y.id AND vp.couple_id = v.couple_id)
               THEN '/why-fun/activities/' || y.id || '/photo?thumbnail=true' END
        FROM why_fun_visits v JOIN why_fun_venues y ON y.id = v.venue_id AND y.couple_id = v.couple_id
        WHERE v.couple_id = :coupleId AND v.scheduled_at <= :today
          AND (CAST(:zoneId AS bigint) IS NULL OR v.city_id = CAST(:zoneId AS bigint))
      ), event_rows AS (
        SELECT s.id AS special_date_id,
          s.special_date AS occurred_on, s.ends_on AS ends_on,
          e.section, e.experience_id, e.image_url
        FROM experience_events e JOIN special_dates s
          ON s.couple_id = e.couple_id AND s.recurrence = 'ONCE'
          AND daterange(s.special_date, s.ends_on, '[]') @> e.occurred_on
        WHERE CAST(:specialDateId AS bigint) IS NULL OR s.id = :specialDateId
        UNION ALL
        SELECT s.id, recurring_window.starts_on,
          recurring_window.starts_on + (s.ends_on - s.special_date),
          e.section, e.experience_id, e.image_url
        FROM experience_events e JOIN special_dates s
          ON s.couple_id = e.couple_id AND s.recurrence = 'ANNUAL'
        CROSS JOIN LATERAL (
          SELECT candidate.starts_on
          FROM generate_series(EXTRACT(YEAR FROM e.occurred_on)::int - 1,
                               EXTRACT(YEAR FROM e.occurred_on)::int) AS years(year_value)
          CROSS JOIN LATERAL (
            SELECT make_date(years.year_value, EXTRACT(MONTH FROM s.special_date)::int, 1)
              + LEAST(EXTRACT(DAY FROM s.special_date)::int,
                  EXTRACT(DAY FROM (make_date(years.year_value,
                    EXTRACT(MONTH FROM s.special_date)::int, 1) + INTERVAL '1 month'
                    - INTERVAL '1 day'))::int) - 1 AS starts_on
          ) candidate
          WHERE e.occurred_on BETWEEN candidate.starts_on
            AND candidate.starts_on + (s.ends_on - s.special_date)
          ORDER BY candidate.starts_on DESC
          LIMIT 1
        ) recurring_window
        WHERE CAST(:specialDateId AS bigint) IS NULL OR s.id = :specialDateId
        UNION ALL
        SELECT s.id, recurring_window.starts_on,
          recurring_window.starts_on + (s.ends_on - s.special_date),
          e.section, e.experience_id, e.image_url
        FROM experience_events e JOIN special_dates s
          ON s.couple_id = e.couple_id AND s.recurrence = 'MONTHLY'
        CROSS JOIN LATERAL (
          SELECT candidate.starts_on
          FROM generate_series(0, 1) AS offsets(months_back)
          CROSS JOIN LATERAL (
            SELECT (date_trunc('month', e.occurred_on)
                    - offsets.months_back * INTERVAL '1 month')::date AS month_start
          ) month_value
          CROSS JOIN LATERAL (
            SELECT month_value.month_start
              + LEAST(EXTRACT(DAY FROM s.special_date)::int,
                  EXTRACT(DAY FROM (month_value.month_start + INTERVAL '1 month'
                    - INTERVAL '1 day'))::int) - 1 AS starts_on
          ) candidate
          WHERE e.occurred_on BETWEEN candidate.starts_on
            AND candidate.starts_on + (s.ends_on - s.special_date)
          ORDER BY candidate.starts_on DESC
          LIMIT 1
        ) recurring_window
        WHERE CAST(:specialDateId AS bigint) IS NULL OR s.id = :specialDateId
        UNION ALL
        SELECT s.id, e.occurred_on, e.occurred_on,
          e.section, e.experience_id, e.image_url
        FROM experience_events e JOIN special_dates s
          ON s.couple_id = e.couple_id AND s.recurrence = 'DAILY'
        WHERE CAST(:specialDateId AS bigint) IS NULL OR s.id = :specialDateId
      ), event_summary AS (
        SELECT special_date_id, occurred_on, MAX(ends_on) AS ends_on, COUNT(*) AS experience_count,
          (ARRAY_AGG(image_url ORDER BY section, experience_id) FILTER (WHERE image_url IS NOT NULL))[1] AS image_url
        FROM event_rows GROUP BY special_date_id, occurred_on
      ), summary_keys AS (
        SELECT special_date_id, occurred_on FROM event_summary
        UNION
        SELECT o.special_date_id, o.occurred_on FROM special_date_occurrences o
        WHERE o.couple_id = :coupleId AND (o.occurred_on <= :today OR o.stage_id IS NOT NULL)
          AND (CAST(:specialDateId AS bigint) IS NULL OR o.special_date_id = :specialDateId)
          AND (CAST(:zoneId AS bigint) IS NULL OR o.city_id = CAST(:zoneId AS bigint) OR EXISTS (
            SELECT 1 FROM event_summary filtered_event
            WHERE filtered_event.special_date_id = o.special_date_id
              AND filtered_event.occurred_on = o.occurred_on))
      )
      SELECT s.id AS special_date_id, s.label AS label, COALESCE(s.recurrence, 'ONCE') AS recurrence,
        k.occurred_on AS occurred_on, COALESCE(es.experience_count, 0) AS experience_count,
        COALESCE(o.ends_on, es.ends_on,
          CASE WHEN s.recurrence = 'ONCE' THEN s.ends_on ELSE k.occurred_on END) AS ends_on,
        CASE WHEN o.cover_photo_id IS NOT NULL THEN '/when-dates/photos/' || o.cover_photo_id || '?thumbnail=true'
             ELSE es.image_url END AS image_url
      FROM summary_keys k
      JOIN special_dates s ON s.id = k.special_date_id AND s.couple_id = :coupleId
      LEFT JOIN event_summary es ON es.special_date_id = k.special_date_id AND es.occurred_on = k.occurred_on
      LEFT JOIN special_date_occurrences o ON o.special_date_id = k.special_date_id AND o.occurred_on = k.occurred_on AND o.couple_id = :coupleId
      ORDER BY k.occurred_on DESC, s.label ASC, s.id ASC
      LIMIT :limit OFFSET :offset
      """, nativeQuery = true)
    List<WhenDateSummaryProjection> findSummaryPageByCoupleId(@Param("coupleId") java.util.UUID coupleId,
        @Param("specialDateId") Long specialDateId, @Param("zoneId") Long zoneId, @Param("today") LocalDate today,
        @Param("limit") int limit, @Param("offset") long offset);
    default List<WhenDateSummaryProjection> findSummaryPageByCoupleId(java.util.UUID coupleId,
        Long specialDateId, LocalDate today, int limit, long offset) {
      return findSummaryPageByCoupleId(coupleId, specialDateId, null, today, limit, offset);
    }
  }
  public interface SpecialDateOccurrences extends CoupleScopedRepository<SpecialDateOccurrence> {
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findBySpecialDateIdAndOccurredOnAndCoupleId(Long specialDateId, LocalDate occurredOn, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findDetailedBySpecialDateIdAndOccurredOnAndCoupleId(Long specialDateId, LocalDate occurredOn, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findFirstBySpecialDateIdAndOccurredOnLessThanEqualAndEndsOnGreaterThanEqualAndCoupleId(Long specialDateId, LocalDate occurredOn, LocalDate sameDay, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findDetailedBySpecialDateIdAndOccurredOnLessThanEqualAndEndsOnGreaterThanEqualAndCoupleId(Long specialDateId, LocalDate occurredOn, LocalDate sameDay, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findBySpecialDateIdInAndOccurredOnBetweenAndCoupleId(Collection<Long> specialDateIds, LocalDate from, LocalDate to, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findBySpecialDateIdInAndOccurredOnLessThanEqualAndEndsOnGreaterThanEqualAndCoupleId(Collection<Long> specialDateIds, LocalDate to, LocalDate from, java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findAllByCoupleIdOrderByOccurredOnDescIdDesc(java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findByCoupleIdAndOccurredOnLessThanEqualOrderByOccurredOnDescIdDesc(java.util.UUID coupleId, LocalDate occurredOn);
    }
  public interface SpecialDateOccurrenceComments extends CoupleScopedRepository<SpecialDateOccurrenceComment> {
   @EntityGraph(attributePaths = {"author", "updatedBy"}) List<SpecialDateOccurrenceComment> findByOccurrenceIdAndCoupleIdOrderByAuthorUsername(Long occurrenceId, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"author", "updatedBy"}) Optional<SpecialDateOccurrenceComment> findByOccurrenceIdAndAuthorIdAndCoupleId(Long occurrenceId, Long authorId, java.util.UUID coupleId);
  }
  public interface SpecialDateOccurrencePhotos extends CoupleScopedRepository<SpecialDateOccurrencePhoto> {
   @EntityGraph(attributePaths = {"occurrence", "occurrence.specialDate", "createdBy"}) List<SpecialDateOccurrencePhoto> findByOccurrenceIdAndCoupleIdOrderByPositionAscIdAsc(Long occurrenceId, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"occurrence", "occurrence.specialDate", "createdBy"}) Optional<SpecialDateOccurrencePhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   long countByOccurrenceIdAndCoupleId(Long occurrenceId, java.util.UUID coupleId);
  }
  public interface Settings extends JpaRepository<GlobalSettings, Integer> {
   @Modifying @Query(value = "insert into global_settings (id, catalog_page_size) values (1, 5) on conflict (id) do nothing", nativeQuery = true) int insertDefaultIfMissing();
  }

  public interface Zones extends JpaRepository<Zone, Long> {
   List<Zone> findByActiveTrueOrderByNameAsc();
   List<Zone> findAllByOrderByNameAsc();
   Optional<Zone> findByNameIgnoreCase(String name);
   Optional<Zone> findByCountryCodeAndNameIgnoreCase(String countryCode,String name);
  }

  public interface Places extends CoupleScopedRepository<Place> {
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) List<Place> findAllByCoupleId(java.util.UUID coupleId);
  @Query(value = """
          with visit_metrics as (
              select visit.place_id, count(distinct visit.id) as visit_count,
                     avg(review.taste) filter (where review.taste is not null) as taste,
                     avg(review.price) filter (where review.price is not null) as price
              from place_visits visit
              left join place_visit_reviews review
                on review.visit_id = visit.id and review.couple_id = visit.couple_id
              where visit.couple_id = :coupleId
              group by visit.place_id
          ), venue_metrics as (
              select review.place_id, avg(score.value) as venue
              from place_reviews review
              cross join lateral (values (review.location), (review.heating), (review.bathrooms),
                                        (review.exterior), (review.seating), (review.service),
                                        (review.ambiance)) as score(value)
              where review.couple_id = :coupleId and score.value is not null
              group by review.place_id
          ), rating_parts as (
              select place.id,
                     coalesce(nullif(visit.taste, 0), 0) + coalesce(nullif(visit.price, 0), 0)
                       + coalesce(nullif(venue.venue, 0), 0) as rating_total,
                     (case when nullif(visit.taste, 0) is null then 0 else 1 end
                       + case when nullif(visit.price, 0) is null then 0 else 1 end
                       + case when nullif(venue.venue, 0) is null then 0 else 1 end) as rating_count
              from places place
              left join visit_metrics visit on visit.place_id = place.id
              left join venue_metrics venue on venue.place_id = place.id
              where place.couple_id = :coupleId and place.deactivated_at is null
          ), place_ratings as (
              select id, case when rating_count = 0 then 0 else rating_total / rating_count end as rating
              from rating_parts
          )
          select place.id
          from places place
          left join categories category on category.id = place.category_id
          join place_ratings rating on rating.id = place.id
          where place.couple_id = :coupleId and place.deactivated_at is null
            and (cast(:zoneId as bigint) is null or place.zone_id = cast(:zoneId as bigint))
            and (cast(:categoryId as bigint) is null or place.category_id = cast(:categoryId as bigint))
            and (cast(:status as text) is null or place.status = cast(:status as text))
            and (cast(:highlightTagId as bigint) is null
                 or exists (select 1 from place_highlight_tags tag
                            where tag.place_id = place.id and tag.couple_id = place.couple_id
                              and tag.tag_id = cast(:highlightTagId as bigint)))
            and (cast(:search as text) is null
                 or position(cast(:search as text) in lower(place.name)) > 0
                 or position(cast(:search as text) in lower(category.name)) > 0
                 or position(cast(:search as text) in lower(place.address)) > 0)
          order by
            case when cast(:sort as text) in ('rating', 'rating-desc') then rating.rating end desc,
            case when cast(:sort as text) = 'rating-asc' then rating.rating end asc,
            case when cast(:sort as text) in ('date', 'date-desc') then place.updated_at end desc,
            case when cast(:sort as text) in ('date', 'date-desc') then place.created_at end desc,
            case when cast(:sort as text) = 'date-asc' then place.updated_at end asc,
            case when cast(:sort as text) = 'date-asc' then place.created_at end asc,
            place.id desc
          limit :limit offset :offset
          """, nativeQuery = true)
  List<Long> findPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
          @Param("zoneId") Long zoneId,
          @Param("categoryId") Long categoryId, @Param("highlightTagId") Long highlightTagId,
          @Param("status") String status, @Param("search") String search, @Param("sort") String sort,
          @Param("limit") int limit, @Param("offset") long offset);
  @Query(value = """
          select place.id from places place
          where place.couple_id = :coupleId and place.deactivated_at is not null
            and (cast(:zoneId as bigint) is null or place.zone_id = cast(:zoneId as bigint))
          order by place.deactivated_at desc, place.id desc
          limit :limit offset :offset
          """, nativeQuery = true)
  List<Long> findArchivedPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
          @Param("zoneId") Long zoneId,
          @Param("limit") int limit, @Param("offset") long offset);
  default List<Long> findArchivedPageIdsByCoupleId(java.util.UUID coupleId, int limit, long offset) {
    return findArchivedPageIdsByCoupleId(coupleId, null, limit, offset);
  }
  default List<Long> findPageIdsByCoupleId(java.util.UUID coupleId, Long categoryId, Long highlightTagId,
          String status, String search, String sort, int limit, long offset) {
    return findPageIdsByCoupleId(coupleId, null, categoryId, highlightTagId, status, search, sort, limit, offset);
  }
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"})
  @Query("select place from Place place where place.id in :ids and place.coupleId = :coupleId and place.deactivatedAt is not null")
  List<Place> findArchivedByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
          @Param("coupleId") java.util.UUID coupleId);
  @EntityGraph(attributePaths = {"category", "createdBy", "updatedBy", "highlightTags"})
  @Query("select place from Place place where place.id in :ids and place.coupleId = :coupleId and place.deactivatedAt is null")
  List<Place> findActiveByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
          @Param("coupleId") java.util.UUID coupleId);
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) @Query("select p from Place p where p.id=:id and p.coupleId=:coupleId") Optional<Place> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
 }

 public interface PlaceMetric {
  Long getPlaceId(); Long getItemCount(); Double getTasteAverage(); Double getPriceAverage();
 }

  public interface VenueMetric {
   Long getPlaceId(); Double getVenueAverage();
  }

  public interface PlaceReviewSummary {
   Long getPlaceId(); String getAuthor(); String getComment(); Short getLocation(); Short getHeating(); Short getBathrooms(); Short getExterior(); Short getSeating(); Short getService(); Short getAmbiance();
  }

  public interface ReviewAuthor {
   Long getReviewId(); String getAuthor();
  }

     public interface PlaceVisits extends CoupleScopedRepository<PlaceVisit> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select visit from PlaceVisit visit where visit.id = :id and visit.coupleId = :coupleId")
        Optional<PlaceVisit> findLockedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findAllByCoupleId(java.util.UUID coupleId);
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"})
        List<PlaceVisit> findByPlaceIdInAndCoupleIdOrderByPlaceIdAscVisitedOnDescIdDesc(
                Collection<Long> placeIds, java.util.UUID coupleId);
        @Query("select v.id from PlaceVisit v where v.place.id = :placeId and v.coupleId = :coupleId order by v.visitedOn desc, v.id desc")
        List<Long> findFirstHistoryPageIdsByPlaceIdAndCoupleId(@Param("placeId") Long placeId,
                @Param("coupleId") java.util.UUID coupleId, Pageable pageable);
        @Query("select v.id from PlaceVisit v where v.place.id = :placeId and v.coupleId = :coupleId and (v.visitedOn < :cursorDate or (v.visitedOn = :cursorDate and v.id < :cursorId)) order by v.visitedOn desc, v.id desc")
        List<Long> findHistoryPageIdsAfterCursor(@Param("placeId") Long placeId,
                @Param("coupleId") java.util.UUID coupleId, @Param("cursorDate") LocalDate cursorDate,
                @Param("cursorId") Long cursorId, Pageable pageable);
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"})
        @Query("select v from PlaceVisit v where v.id in :ids and v.place.id = :placeId and v.coupleId = :coupleId")
        List<PlaceVisit> findAllByIdInAndPlaceIdAndCoupleId(@Param("ids") Collection<Long> ids, @Param("placeId") Long placeId, @Param("coupleId") java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByCoupleIdAndVisitedOnOrderByVisitedOnDescIdDesc(java.util.UUID coupleId, LocalDate visitedOn);
      @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByCoupleIdAndVisitedOnBetweenOrderByVisitedOnDescIdDesc(java.util.UUID coupleId, LocalDate from, LocalDate to);
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findByPlaceIdAndVisitedOnAndCoupleId(Long placeId, LocalDate visitedOn, java.util.UUID coupleId);
      boolean existsByPlaceIdAndCoupleId(Long placeId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   }

  public interface PlaceVisitPhotos extends CoupleScopedRepository<PlaceVisitPhoto> {
     @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(Long visitId, java.util.UUID coupleId);
     long countByVisitIdAndCoupleId(Long visitId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdInAndCoupleIdOrderByVisitIdAscPositionAscIdAsc(Collection<Long> visitIds, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) Optional<PlaceVisitPhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   }

   public interface PlaceVisitReviews extends CoupleScopedRepository<PlaceVisitReview> {
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdAndCoupleIdOrderByAuthorUsername(Long visitId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdInAndCoupleIdOrderByVisitIdAscAuthorUsername(Collection<Long> visitIds, java.util.UUID coupleId);
    @Query("select r.id as reviewId, author.username as author from PlaceVisitReview r join r.author author where r.visit.id in :visitIds and r.coupleId=:coupleId") List<ReviewAuthor> authorsByVisitIdInAndCoupleId(@Param("visitIds") Collection<Long> visitIds, @Param("coupleId") java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) Optional<PlaceVisitReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<PlaceVisitReview> findByVisitIdAndAuthorIdAndCoupleId(Long visitId, Long authorId, java.util.UUID coupleId);
   }

  public interface Items extends CoupleScopedRepository<Item> {
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"}) Optional<Item> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"}) List<Item> findByVisitIdAndCoupleIdAndDeletedAtIsNullOrderByIdDesc(Long visitId, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.coupleId = :coupleId and i.visit.coupleId = :coupleId and i.deletedAt is null order by i.id desc") List<Item> findCatalogByPlaceIdAndCoupleId(@Param("placeId") Long placeId, @Param("coupleId") java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.coupleId = :coupleId and i.visit.coupleId = :coupleId and i.deletedAt is null order by i.id desc") List<Item> findCatalogByPlaceIdAndCoupleId(@Param("placeId") Long placeId, @Param("coupleId") java.util.UUID coupleId, Pageable pageable);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.visit.visitedOn = :visitDate and i.coupleId = :coupleId and i.visit.coupleId = :coupleId and i.deletedAt is null order by i.id desc") List<Item> findCatalogByPlaceIdAndVisitDateAndCoupleId(@Param("placeId") Long placeId, @Param("visitDate") LocalDate visitDate, @Param("coupleId") java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.visit.visitedOn = :visitDate and i.coupleId = :coupleId and i.visit.coupleId = :coupleId and i.deletedAt is null order by i.id desc") List<Item> findCatalogByPlaceIdAndVisitDateAndCoupleId(@Param("placeId") Long placeId, @Param("visitDate") LocalDate visitDate, @Param("coupleId") java.util.UUID coupleId, Pageable pageable);
   @Query("select distinct i.visit.visitedOn from Item i where i.visit.place.id = :placeId and i.coupleId = :coupleId and i.visit.coupleId = :coupleId and i.deletedAt is null order by i.visit.visitedOn desc")
   List<LocalDate> findItemDatesByPlaceIdAndCoupleId(@Param("placeId") Long placeId,
           @Param("coupleId") java.util.UUID coupleId);
  }

  public interface Photos extends CoupleScopedRepository<ItemPhoto> {
  Optional<ItemPhoto> findByItemIdAndCoupleId(Long id, java.util.UUID coupleId);
  @EntityGraph(attributePaths = "item") List<ItemPhoto> findByItemIdInAndCoupleId(Collection<Long> ids, java.util.UUID coupleId);
 }

  public interface PlaceReviews extends CoupleScopedRepository<PlaceReview> {
   Optional<PlaceReview> findByPlaceIdAndAuthorIdAndCoupleId(Long placeId, Long authorId, java.util.UUID coupleId);
   @Query("select r.place.id as placeId, author.username as author, r.comment as comment, r.location as location, r.heating as heating, r.bathrooms as bathrooms, r.exterior as exterior, r.seating as seating, r.service as service, r.ambiance as ambiance from PlaceReview r join r.author author where r.place.id in :placeIds and r.coupleId = :coupleId order by r.place.id, author.username") List<PlaceReviewSummary> summariesByPlaceIdInAndCoupleId(@Param("placeIds") Collection<Long> placeIds, @Param("coupleId") java.util.UUID coupleId);
   @Query("select r.place.id as placeId, avg((coalesce(r.location, 0) + coalesce(r.heating, 0) + coalesce(r.bathrooms, 0) + coalesce(r.exterior, 0) + coalesce(r.seating, 0) + coalesce(r.service, 0) + coalesce(r.ambiance, 0)) / (case when r.location is null then 0 else 1 end + case when r.heating is null then 0 else 1 end + case when r.bathrooms is null then 0 else 1 end + case when r.exterior is null then 0 else 1 end + case when r.seating is null then 0 else 1 end + case when r.service is null then 0 else 1 end + case when r.ambiance is null then 0 else 1 end)) as venueAverage from PlaceReview r where r.place.id in :ids and r.coupleId=:coupleId group by r.place.id") List<VenueMetric> venueMetricsByCouple(@Param("ids") Collection<Long> ids, @Param("coupleId") java.util.UUID coupleId);
 }

   public interface PlacePhotos extends CoupleScopedRepository<PlacePhoto> {
   Optional<PlacePhoto> findByPlaceIdAndCoupleId(Long placeId, java.util.UUID coupleId);
   @EntityGraph(attributePaths = "place") List<PlacePhoto> findByPlaceIdInAndCoupleId(Collection<Long> placeIds, java.util.UUID coupleId);
  }

  public interface FilmPhotos extends CoupleScopedRepository<FilmPhoto> {
   Optional<FilmPhoto> findByFilmIdAndCoupleId(Long filmId, java.util.UUID coupleId);
   Optional<FilmPhoto> findByIdAndFilmIdAndCoupleId(Long id, Long filmId, java.util.UUID coupleId);
   @Query("select p.id as id, p.film.id as filmId, p.width as width, p.height as height, p.createdAt as createdAt from FilmPhoto p where p.film.id in :filmIds and p.coupleId = :coupleId") List<FilmPhotoMetadata> metadataByFilmIdInAndCoupleId(@Param("filmIds") Collection<Long> filmIds, @Param("coupleId") java.util.UUID coupleId);
  }

  public interface PhotoMetadata {
   Long getId(); Integer getWidth(); Integer getHeight(); Instant getCreatedAt();
  }

  public interface FilmPhotoMetadata extends PhotoMetadata { Long getFilmId(); }

 public interface WatchPlatforms extends JpaRepository<WatchPlatform, Long> {
  List<WatchPlatform> findByActiveTrueOrderByNameAsc();
  List<WatchPlatform> findAllByOrderByNameAsc();
 }

  public interface FilmGenreOptions extends JpaRepository<FilmGenreOption, Long> {
   List<FilmGenreOption> findByActiveTrueOrderByNameAsc();
   List<FilmGenreOption> findAllByOrderByNameAsc();
   List<FilmGenreOption> findAllByNameIn(Collection<String> names);
  }

   public interface ItemReviews extends CoupleScopedRepository<ItemReview> {
     @EntityGraph(attributePaths = {"item", "author"}) List<ItemReview> findByItemIdInOrderByItemIdAscAuthorUsername(Collection<Long> itemIds);
     @EntityGraph(attributePaths = {"item", "author"}) List<ItemReview> findByItemIdInAndCoupleIdOrderByItemIdAscAuthorUsername(Collection<Long> itemIds, java.util.UUID coupleId);
     Optional<ItemReview> findByItemIdAndAuthorIdAndCoupleId(Long itemId, Long authorId, java.util.UUID coupleId);
    @Query("select r.id as reviewId, author.username as author from ItemReview r join r.author author where r.item.id in :itemIds") List<ReviewAuthor> authorsByItemIdIn(@Param("itemIds") Collection<Long> itemIds);
    @Query("select r.id as reviewId, author.username as author from ItemReview r join r.author author where r.item.id in :itemIds and r.coupleId=:coupleId") List<ReviewAuthor> authorsByItemIdInAndCoupleId(@Param("itemIds") Collection<Long> itemIds, @Param("coupleId") java.util.UUID coupleId);
  }

  public interface Films extends CoupleScopedRepository<Film> {
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) List<Film> findAllByCoupleId(java.util.UUID coupleId);
  @Query(value = """
          with film_ratings as (
              select review.film_id, avg(review.rating) as rating
              from film_reviews review
              where review.couple_id = :coupleId
              group by review.film_id
          )
          select film.id
          from films film
          left join film_ratings rating on rating.film_id = film.id
          where film.couple_id = :coupleId
            and (cast(:genre as text) is null or exists (
                select 1 from film_genres fg
                join film_genre_options genre_option on genre_option.id = fg.genre_id
                where fg.film_id = film.id and fg.couple_id = film.couple_id
                  and lower(genre_option.name) = cast(:genre as text)))
            and (cast(:platformId as bigint) is null or film.platform_id = cast(:platformId as bigint))
            and (cast(:watched as boolean) is null
                 or (film.watched_count > 0) = cast(:watched as boolean))
            and (cast(:search as text) is null
                 or position(cast(:search as text) in lower(film.title)) > 0
                 or position(cast(:search as text) in lower(film.original_title)) > 0)
          order by
            case when cast(:sort as text) in ('date', 'date-desc') then film.updated_at end desc,
            case when cast(:sort as text) in ('date', 'date-desc') then film.created_at end desc,
            case when cast(:sort as text) = 'date-asc' then film.updated_at end asc,
            case when cast(:sort as text) = 'date-asc' then film.created_at end asc,
            case when cast(:sort as text) in ('rating', 'rating-desc') then rating.rating end desc nulls last,
            case when cast(:sort as text) = 'rating-asc' then rating.rating end asc nulls last,
            case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then film.updated_at end desc,
            case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then film.created_at end desc,
            film.id desc
          limit :limit offset :offset
          """, nativeQuery = true)
  List<Long> findPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
          @Param("genre") String genre,
          @Param("platformId") Long platformId, @Param("watched") Boolean watched,
          @Param("search") String search, @Param("sort") String sort,
          @Param("limit") int limit, @Param("offset") long offset);
  default List<Long> findPageIdsByCoupleId(java.util.UUID coupleId, Long ignoredZoneId,
          String genre, Long platformId, Boolean watched, String search, String sort,
          int limit, long offset) {
    return findPageIdsByCoupleId(coupleId, genre, platformId, watched, search, sort, limit, offset);
  }
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"})
  @Query("select film from Film film where film.id in :ids and film.coupleId = :coupleId")
  List<Film> findAllByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
          @Param("coupleId") java.util.UUID coupleId);
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) @Query("select f from Film f where f.id=:id and f.coupleId=:coupleId") Optional<Film> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
  Optional<Film> findByTmdbIdAndCoupleId(Long tmdbId, java.util.UUID coupleId);
  }


     public interface FilmReviews extends CoupleScopedRepository<FilmReview> {
    @EntityGraph(attributePaths = {"author", "metrics", "view"}) @Query("select r from FilmReview r where r.film.id=:filmId and r.coupleId=:coupleId order by r.view.watchedOn desc, r.id desc") List<FilmReview> findByFilmIdAndCoupleIdOrderByViewWatchedOnDescIdDesc(@Param("filmId") Long filmId, @Param("coupleId") java.util.UUID coupleId);
     @Query("select r.id as reviewId, author.username as author from FilmReview r join r.author author where r.film.id=:filmId and r.coupleId=:coupleId") List<ReviewAuthor> authorsByFilmIdAndCoupleId(@Param("filmId") Long filmId, @Param("coupleId") java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"author", "metrics", "view", "film"}) Optional<FilmReview> findByIdAndFilmIdAndCoupleId(Long id, Long filmId, java.util.UUID coupleId);
    boolean existsByViewIdAndAuthorIdAndCoupleId(Long viewId, Long authorId, java.util.UUID coupleId);
  }

     public interface FilmViews extends CoupleScopedRepository<FilmView> {
       @EntityGraph(attributePaths = {"film", "createdBy", "updatedBy"}) List<FilmView> findAllByCoupleId(java.util.UUID coupleId);
       @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) List<FilmView> findByFilmIdAndCoupleIdOrderByWatchedOnDescIdDesc(Long filmId, java.util.UUID coupleId);
       @EntityGraph(attributePaths = {"film", "film.platform", "film.genres", "createdBy", "updatedBy"}) List<FilmView> findByCoupleIdAndWatchedOnOrderByWatchedOnDescIdDesc(java.util.UUID coupleId, LocalDate watchedOn);
       @EntityGraph(attributePaths = {"film", "film.platform", "film.genres", "createdBy", "updatedBy"}) List<FilmView> findByCoupleIdAndWatchedOnBetweenOrderByWatchedOnDescIdDesc(java.util.UUID coupleId, LocalDate from, LocalDate to);
      @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) Optional<FilmView> findByIdAndFilmIdAndCoupleId(Long id, Long filmId, java.util.UUID coupleId);
      Optional<FilmView> findByFilmIdAndWatchedOnAndCoupleId(Long filmId, LocalDate watchedOn, java.util.UUID coupleId);
   }

  public interface HomeRecipes extends CoupleScopedRepository<HomeRecipe> {
    @EntityGraph(attributePaths = {"author", "ingredients", "steps", "repeatedFrom"}) Optional<HomeRecipe> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
  }

  public interface HomeRecipePhotos extends CoupleScopedRepository<HomeRecipePhoto> {
  }

  public interface HomeRecipeReviews extends CoupleScopedRepository<HomeRecipeReview> {
   @EntityGraph(attributePaths = {"author", "recipe"}) @Query("select r from HomeRecipeReview r where r.recipe.id in :recipeIds and r.coupleId=:coupleId order by r.recipe.id, r.author.username") List<HomeRecipeReview> findByRecipeIdInAndCoupleIdOrderByAuthorUsername(@Param("recipeIds") Collection<Long> recipeIds, @Param("coupleId") java.util.UUID coupleId);
   @EntityGraph(attributePaths = "author") Optional<HomeRecipeReview> findByRecipeIdAndAuthorIdAndCoupleId(Long recipeId, Long authorId, java.util.UUID coupleId);
  }

  public interface WhyFunCategories extends JpaRepository<WhyFunCategory, Long> {
   @EntityGraph(attributePaths = "parent") List<WhyFunCategory> findAllByOrderByParentIdAscNameAsc();
   @EntityGraph(attributePaths = "parent") Optional<WhyFunCategory> findDetailedById(Long id);
   Optional<WhyFunCategory> findByParentIsNullAndSlug(String slug);
   Optional<WhyFunCategory> findByParentIdAndSlug(Long parentId, String slug);
   boolean existsByParentId(Long parentId);
  }

  public interface WhyFunVenues extends CoupleScopedRepository<WhyFunVenue> {
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select venue from WhyFunVenue venue where venue.id = :id and venue.coupleId = :coupleId")
     Optional<WhyFunVenue> findLockedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "updatedBy", "schedules"}) List<WhyFunVenue> findAllByCoupleId(java.util.UUID coupleId);
   @Query(value = """
           select venue.id
           from why_fun_venues venue
           where venue.couple_id = :coupleId
             and (cast(:zoneId as bigint) is null or venue.zone_id = cast(:zoneId as bigint))
             and (cast(:categoryId as bigint) is null or venue.category_id = cast(:categoryId as bigint))
             and (cast(:subcategoryId as bigint) is null or venue.subcategory_id = cast(:subcategoryId as bigint))
             and (cast(:timeline as text) is null
                  or cast(:timeline as text) not in ('UPCOMING', 'PAST', 'UNSCHEDULED')
                  or (cast(:timeline as text) = 'UPCOMING' and venue.scheduled_at is not null and venue.scheduled_at >= :today)
                  or (cast(:timeline as text) = 'PAST' and venue.scheduled_at is not null and venue.scheduled_at < :today)
                  or (cast(:timeline as text) = 'UNSCHEDULED' and venue.scheduled_at is null))
           order by
             case when cast(:timeline as text) = 'UPCOMING' then venue.scheduled_at end asc nulls last,
             case when cast(:timeline as text) = 'PAST' then venue.scheduled_at end desc nulls last,
             case when cast(:timeline as text) not in ('UPCOMING', 'PAST') or cast(:timeline as text) is null then venue.created_at end desc,
             venue.id desc
           limit :limit offset :offset
           """, nativeQuery = true)
   List<Long> findPlanPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
           @Param("zoneId") Long zoneId,
           @Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId,
           @Param("timeline") String timeline, @Param("today") java.time.LocalDate today,
           @Param("limit") int limit, @Param("offset") long offset);
   default List<Long> findPlanPageIdsByCoupleId(java.util.UUID coupleId, Long categoryId,
           Long subcategoryId, String timeline, java.time.LocalDate today, int limit, long offset) {
     return findPlanPageIdsByCoupleId(coupleId, null, categoryId, subcategoryId, timeline, today, limit, offset);
   }
   @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "updatedBy", "schedules"})
   @Query("select venue from WhyFunVenue venue where venue.id in :ids and venue.coupleId = :coupleId")
   List<WhyFunVenue> findPlansByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
           @Param("coupleId") java.util.UUID coupleId);
   @Query(value = """
           with activity_ratings as (
               select visit.venue_id, avg(review.rating) as rating
               from why_fun_visits visit
               join why_fun_visit_reviews review
                 on review.visit_id = visit.id and review.couple_id = visit.couple_id
               where visit.couple_id = :coupleId
               group by visit.venue_id
           )
           select venue.id
           from why_fun_venues venue
           join why_fun_categories category on category.id = venue.category_id
           join why_fun_categories subcategory on subcategory.id = venue.subcategory_id
           left join activity_ratings rating on rating.venue_id = venue.id
           where venue.couple_id = :coupleId
             and (cast(:zoneId as bigint) is null or venue.zone_id = cast(:zoneId as bigint))
             and (cast(:categoryId as bigint) is null or venue.category_id = cast(:categoryId as bigint))
             and (cast(:subcategoryId as bigint) is null or venue.subcategory_id = cast(:subcategoryId as bigint))
             and (cast(:search as text) is null
                  or position(cast(:search as text) in lower(venue.name)) > 0
                  or position(cast(:search as text) in lower(venue.address)) > 0
                  or position(cast(:search as text) in lower(category.name)) > 0
                  or position(cast(:search as text) in lower(subcategory.name)) > 0)
             and (cast(:visited as boolean) is null
                  or exists (select 1 from why_fun_visits v
                             where v.couple_id = venue.couple_id and v.venue_id = venue.id)
                     = cast(:visited as boolean))
           order by
             case when cast(:sort as text) = 'name' then lower(venue.name) end asc,
             case when cast(:sort as text) in ('date', 'date-desc') then venue.updated_at end desc,
             case when cast(:sort as text) in ('date', 'date-desc') then venue.created_at end desc,
             case when cast(:sort as text) = 'date-asc' then venue.updated_at end asc,
             case when cast(:sort as text) = 'date-asc' then venue.created_at end asc,
             case when cast(:sort as text) in ('rating', 'rating-desc') then rating.rating end desc nulls last,
             case when cast(:sort as text) = 'rating-asc' then rating.rating end asc nulls last,
             case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then venue.updated_at end desc,
             case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then venue.created_at end desc,
             case when cast(:sort as text) = 'name' then venue.id end asc,
             venue.id desc
           limit :limit offset :offset
           """, nativeQuery = true)
   List<Long> findPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
           @Param("zoneId") Long zoneId,
           @Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId,
           @Param("search") String search, @Param("visited") Boolean visited, @Param("sort") String sort,
           @Param("limit") int limit, @Param("offset") long offset);
   default List<Long> findPageIdsByCoupleId(java.util.UUID coupleId, Long categoryId, Long subcategoryId,
           String search, Boolean visited, String sort, int limit, long offset) {
     return findPageIdsByCoupleId(coupleId, null, categoryId, subcategoryId, search, visited, sort, limit, offset);
   }
   @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "updatedBy", "schedules"})
   @Query("select venue from WhyFunVenue venue where venue.id in :ids and venue.coupleId = :coupleId")
   List<WhyFunVenue> findAllByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
           @Param("coupleId") java.util.UUID coupleId);
   @Query("select v from WhyFunVenue v join fetch v.category join fetch v.subcategory join fetch v.createdBy where v.coupleId = :coupleId and (:categoryId is null or v.category.id = :categoryId) and (:subcategoryId is null or v.subcategory.id = :subcategoryId) and (:cursor is null or v.id < :cursor) order by v.id desc") List<WhyFunVenue> list(@Param("coupleId") java.util.UUID coupleId, @Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId, @Param("cursor") Long cursor, Pageable pageable);
     @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "updatedBy", "schedules"}) @Query("select v from WhyFunVenue v where v.id=:id and v.coupleId=:coupleId") Optional<WhyFunVenue> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
  }

  public interface WhyFunVenuePhotos extends CoupleScopedRepository<WhyFunVenuePhoto> {
    List<WhyFunVenuePhoto> findByVenueIdAndCoupleIdOrderByIdAsc(Long venueId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = "venue") @Query("select p from WhyFunVenuePhoto p where p.venue.id in :venueIds and p.coupleId=:coupleId order by p.venue.id asc, p.id asc") List<WhyFunVenuePhoto> findByVenueIdInAndCoupleIdOrderByVenueIdAscIdAsc(@Param("venueIds") Collection<Long> venueIds, @Param("coupleId") java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"venue", "venue.createdBy"}) Optional<WhyFunVenuePhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   Optional<WhyFunVenuePhoto> findByIdAndVenueIdAndCoupleId(Long id, Long venueId, java.util.UUID coupleId);
     @Query("select p.id as id, p.width as width, p.height as height, p.createdAt as createdAt from WhyFunVenuePhoto p where p.id in :photoIds and p.coupleId = :coupleId") List<PhotoMetadata> metadataByIdInAndCoupleId(@Param("photoIds") Collection<Long> photoIds, @Param("coupleId") java.util.UUID coupleId);
   long countByVenueIdAndCoupleId(Long venueId, java.util.UUID coupleId);
   }

   public interface WhyFunReviewSummary {
    Long getId(); Long getVenueId(); String getAuthor(); Short getRating(); String getComment(); java.time.Instant getUpdatedAt();
   }

    public interface ActivityRating {
     Long getActivityId(); Double getRating();
    }

    public interface ActivityVisitCount {
     Long getActivityId(); Long getVisitCount();
    }

   public interface WhyFunVenueReviews extends CoupleScopedRepository<WhyFunVenueReview> {
   @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id=:venueId order by u.username") List<WhyFunReviewSummary> summariesByVenueId(@Param("venueId") Long venueId);
   @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id=:venueId and r.coupleId=:coupleId order by u.username") List<WhyFunReviewSummary> summariesByVenueIdAndCoupleId(@Param("venueId") Long venueId, @Param("coupleId") java.util.UUID coupleId);
    @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id in :venueIds order by r.venue.id asc, u.username") List<WhyFunReviewSummary> summariesByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
    @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id in :venueIds and r.coupleId=:coupleId order by r.venue.id asc, u.username") List<WhyFunReviewSummary> summariesByVenueIdInAndCoupleId(@Param("venueIds") Collection<Long> venueIds, @Param("coupleId") java.util.UUID coupleId);
   @EntityGraph(attributePaths = "author") Optional<WhyFunVenueReview> findByVenueIdAndAuthorIdAndCoupleId(Long venueId, Long authorId, java.util.UUID coupleId);
   }

    public interface WhyFunVisits extends CoupleScopedRepository<WhyFunVisit> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from WhyFunVisit visit where visit.id = :id and visit.coupleId = :coupleId")
    Optional<WhyFunVisit> findLockedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
    @Query("select visit.id from WhyFunVisit visit where visit.venue.id = :venueId and visit.coupleId = :coupleId and ((:cursorDate is null and :cursorId is null) or (:cursorDate is not null and (visit.scheduledAt < :cursorDate or (visit.scheduledAt = :cursorDate and visit.id < :cursorId) or visit.scheduledAt is null)) or (:cursorDate is null and :cursorId is not null and visit.scheduledAt is null and visit.id < :cursorId)) order by visit.scheduledAt desc nulls last, visit.id desc")
    List<Long> findActivityHistoryPageIds(@Param("venueId") Long venueId, @Param("coupleId") java.util.UUID coupleId,
            @Param("cursorDate") LocalDate cursorDate, @Param("cursorId") Long cursorId, Pageable pageable);
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.createdBy", "venue.updatedBy", "venue.schedules", "createdBy", "updatedBy"})
    List<WhyFunVisit> findAllByIdInAndVenueIdAndCoupleId(Collection<Long> ids, Long venueId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.createdBy", "venue.updatedBy", "venue.schedules", "createdBy", "updatedBy"}) Optional<WhyFunVisit> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.createdBy", "venue.updatedBy", "venue.schedules", "createdBy", "updatedBy"}) List<WhyFunVisit> findByCoupleIdAndScheduledAtOrderByScheduledAtDescIdDesc(java.util.UUID coupleId, LocalDate scheduledAt);
   @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.createdBy", "venue.updatedBy", "venue.schedules", "createdBy", "updatedBy"}) List<WhyFunVisit> findByCoupleIdAndScheduledAtBetweenOrderByScheduledAtDescIdDesc(java.util.UUID coupleId, LocalDate from, LocalDate to);
     @Query("select v.venue.id as activityId, count(v) as visitCount from WhyFunVisit v where v.venue.id in :activityIds and v.coupleId=:coupleId group by v.venue.id") List<ActivityVisitCount> countsByActivityIdInAndCoupleId(@Param("activityIds") Collection<Long> activityIds, @Param("coupleId") java.util.UUID coupleId);
   }

   public interface WhyFunVisitPhotos extends CoupleScopedRepository<WhyFunVisitPhoto> {
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) List<WhyFunVisitPhoto> findByVisitIdAndCoupleIdOrderByPositionAscIdAsc(Long visitId, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) Optional<WhyFunVisitPhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   }

   public interface WhyFunVisitReviews extends CoupleScopedRepository<WhyFunVisitReview> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<WhyFunVisitReview> findByVisitIdAndCoupleIdOrderByAuthorUsername(Long visitId, java.util.UUID coupleId);
    @Query("select r.visit.venue.id as activityId, avg(r.rating) as rating from WhyFunVisitReview r where r.visit.venue.id in :activityIds and r.coupleId=:coupleId group by r.visit.venue.id") List<ActivityRating> ratingsByActivityIdInAndCoupleId(@Param("activityIds") Collection<Long> activityIds, @Param("coupleId") java.util.UUID coupleId);
     @Query("select r.id as reviewId, author.username as author from WhyFunVisitReview r join r.author author where r.visit.id=:visitId and r.coupleId=:coupleId") List<ReviewAuthor> authorsByVisitIdAndCoupleId(@Param("visitId") Long visitId, @Param("coupleId") java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "author", "updatedBy"}) Optional<WhyFunVisitReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<WhyFunVisitReview> findByVisitIdAndAuthorIdAndCoupleId(Long visitId, Long authorId, java.util.UUID coupleId);
   }

   public interface Recipes extends CoupleScopedRepository<Recipe> {
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) Optional<Recipe> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) List<Recipe> findAllByCoupleId(java.util.UUID coupleId);
    @Query(value = """
            with recipe_ratings as (
                select c.recipe_id, avg(review.rating) as rating
                from cookings c
                join cooking_reviews review
                  on review.cooking_id = c.id and review.couple_id = c.couple_id
                where c.couple_id = :coupleId
                group by c.recipe_id
            )
            select r.id
            from recipes r
            left join recipe_ratings rr on rr.recipe_id = r.id
            where r.couple_id = :coupleId
              and (cast(:search as text) is null
                   or position(cast(:search as text) in lower(r.name)) > 0)
              and (cast(:home as text) is null
                   or exists (select 1 from cookings ch
                              where ch.couple_id = r.couple_id and ch.recipe_id = r.id
                                and ch.home = cast(:home as text)))
              and (cast(:cooked as boolean) is null
                   or exists (select 1 from cookings cc
                              where cc.couple_id = r.couple_id and cc.recipe_id = r.id)
                      = cast(:cooked as boolean))
            order by
              case when cast(:sort as text) in ('date', 'date-desc') then r.updated_at end desc,
              case when cast(:sort as text) in ('date', 'date-desc') then r.created_at end desc,
              case when cast(:sort as text) = 'date-asc' then r.updated_at end asc,
              case when cast(:sort as text) = 'date-asc' then r.created_at end asc,
              case when cast(:sort as text) in ('rating', 'rating-desc') then rr.rating end desc nulls last,
              case when cast(:sort as text) = 'rating-asc' then rr.rating end asc nulls last,
              case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then r.updated_at end desc,
              case when cast(:sort as text) in ('rating', 'rating-desc', 'rating-asc') then r.created_at end desc,
              r.id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<Long> findPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId,
            @Param("search") String search, @Param("home") String home, @Param("cooked") Boolean cooked,
            @Param("sort") String sort, @Param("limit") int limit, @Param("offset") long offset);
    default List<Long> findPageIdsByCoupleId(java.util.UUID coupleId, Long ignoredZoneId,
            String search, String home, Boolean cooked, String sort, int limit, long offset) {
      return findPageIdsByCoupleId(coupleId, search, home, cooked, sort, limit, offset);
    }
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"})
    @Query("select r from Recipe r where r.id in :ids and r.coupleId = :coupleId")
    List<Recipe> findAllByIdInAndCoupleId(@Param("ids") Collection<Long> ids,
            @Param("coupleId") java.util.UUID coupleId);
   }

    public interface RecipePhotos extends CoupleScopedRepository<RecipePhoto> {
     Optional<RecipePhoto> findByRecipeIdAndCoupleId(Long recipeId, java.util.UUID coupleId);
     Optional<RecipePhoto> findByIdAndRecipeIdAndCoupleId(Long id, Long recipeId, java.util.UUID coupleId);
     @Query("select p.id as id, p.recipe.id as recipeId, p.width as width, p.height as height, p.createdAt as createdAt from RecipePhoto p where p.recipe.id in :recipeIds") List<RecipePhotoMetadata> metadataByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
     @Query("select p.id as id, p.recipe.id as recipeId, p.width as width, p.height as height, p.createdAt as createdAt from RecipePhoto p where p.recipe.id in :recipeIds and p.coupleId=:coupleId") List<RecipePhotoMetadata> metadataByRecipeIdInAndCoupleId(@Param("recipeIds") Collection<Long> recipeIds, @Param("coupleId") java.util.UUID coupleId);
    }

    public interface RecipePhotoMetadata extends PhotoMetadata { Long getRecipeId(); }

    public interface RecipeCookingCount { Long getRecipeId(); Long getCookingCount(); }
    public interface RecipeHome { Long getRecipeId(); Home getHome(); }
   public interface RecipeRating { Long getRecipeId(); Double getRating(); Double getComplexityRating(); Double getTasteRating(); }

    public interface Cookings extends CoupleScopedRepository<Cooking> {
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findAllByCoupleId(java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByCoupleIdAndHomeOrderByCookedOnDescIdDesc(java.util.UUID coupleId, Home home);
    @Query(value = "select c.id from cookings c join recipes r on r.id=c.recipe_id and r.couple_id=c.couple_id where c.couple_id = :coupleId and (cast(:recipeId as bigint) is null or c.recipe_id = :recipeId) and (cast(:home as text) is null or c.home = :home) order by c.cooked_on desc, c.id desc limit :limit offset :offset", nativeQuery = true)
    List<Long> findPageIdsByCoupleId(@Param("coupleId") java.util.UUID coupleId, @Param("recipeId") Long recipeId,
            @Param("home") String home, @Param("limit") int limit, @Param("offset") long offset);
    default List<Long> findPageIdsByCoupleId(java.util.UUID coupleId, Long ignoredZoneId,
            Long recipeId, String home, int limit, long offset) {
      return findPageIdsByCoupleId(coupleId, recipeId, home, limit, offset);
    }
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"})
    @Query("select c from Cooking c where c.id in :ids and c.coupleId = :coupleId")
    List<Cooking> findAllByIdInAndCoupleId(@Param("ids") Collection<Long> ids, @Param("coupleId") java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByCoupleIdAndCookedOnOrderByCookedOnDescIdDesc(java.util.UUID coupleId, LocalDate cookedOn);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByCoupleIdAndCookedOnBetweenOrderByCookedOnDescIdDesc(java.util.UUID coupleId, LocalDate from, LocalDate to);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) Optional<Cooking> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
     boolean existsByRecipeIdAndCoupleId(Long recipeId, java.util.UUID coupleId);
     @Query("select c.recipe.id as recipeId, count(c) as cookingCount from Cooking c where c.recipe.id in :recipeIds and c.coupleId=:coupleId group by c.recipe.id") List<RecipeCookingCount> cookingCountsByRecipeIdInAndCoupleId(@Param("recipeIds") Collection<Long> recipeIds, @Param("coupleId") java.util.UUID coupleId);
     @Query("select distinct c.recipe.id as recipeId, c.home as home from Cooking c where c.recipe.id in :recipeIds and c.coupleId=:coupleId") List<RecipeHome> homesByRecipeIdInAndCoupleId(@Param("recipeIds") Collection<Long> recipeIds, @Param("coupleId") java.util.UUID coupleId);
   }

   public interface CookingReviews extends CoupleScopedRepository<CookingReview> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<CookingReview> findByCookingIdAndCoupleIdOrderByAuthorUsername(Long cookingId, java.util.UUID coupleId);
     @Query("select r.id as reviewId, author.username as author from CookingReview r join r.author author where r.cooking.id=:cookingId and r.coupleId=:coupleId") List<ReviewAuthor> authorsByCookingIdAndCoupleId(@Param("cookingId") Long cookingId, @Param("coupleId") java.util.UUID coupleId);
    @Query("select r.cooking.recipe.id as recipeId, avg(r.rating) as rating, avg(r.complexity) as complexityRating, avg(r.taste) as tasteRating from CookingReview r where r.cooking.recipe.id in :recipeIds and r.coupleId=:coupleId group by r.cooking.recipe.id") List<RecipeRating> ratingsByRecipeIdInAndCoupleId(@Param("recipeIds") Collection<Long> recipeIds, @Param("coupleId") java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"cooking", "cooking.recipe", "author", "updatedBy"}) Optional<CookingReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<CookingReview> findByCookingIdAndAuthorIdAndCoupleId(Long cookingId, Long authorId, java.util.UUID coupleId);
   }
}
