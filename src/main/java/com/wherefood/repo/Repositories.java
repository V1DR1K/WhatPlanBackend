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

 public interface Users extends JpaRepository<User, Long> {
   Optional<User> findByUsernameIgnoreCase(String username);
   Optional<User> findByAuthUserId(java.util.UUID authUserId);
 }

 public interface Couples extends JpaRepository<Couple, java.util.UUID> {
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select c from Couple c where c.id = :id")
   Optional<Couple> findLockedById(@Param("id") java.util.UUID id);
 }

 public interface CoupleMembers extends JpaRepository<CoupleMember, Long> {
   @Query("select m.couple.id from CoupleMember m where m.user.id = :userId and m.status = com.wherefood.domain.CoupleMemberStatus.ACTIVE")
   Optional<java.util.UUID> findActiveCoupleIdByUserId(@Param("userId") Long userId);

   @EntityGraph(attributePaths = {"user"})
   List<CoupleMember> findByCoupleIdAndStatusOrderBySlot(java.util.UUID coupleId, CoupleMemberStatus status);

   @EntityGraph(attributePaths = {"couple", "user"})
   Optional<CoupleMember> findByCoupleIdAndUserIdAndStatus(java.util.UUID coupleId, Long userId, CoupleMemberStatus status);

   long countByCoupleIdAndStatus(java.util.UUID coupleId, CoupleMemberStatus status);
 }

 public interface CoupleInvitations extends JpaRepository<CoupleInvitation, Long> {
   @EntityGraph(attributePaths = {"couple", "createdBy"})
   Optional<CoupleInvitation> findByTokenHash(String tokenHash);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @EntityGraph(attributePaths = {"couple", "createdBy"})
   Optional<CoupleInvitation> findLockedById(Long id);

   @EntityGraph(attributePaths = {"couple"})
   List<CoupleInvitation> findByCoupleIdAndStatusOrderByCreatedAtDesc(java.util.UUID coupleId, CoupleInvitationStatus status);

   Optional<CoupleInvitation> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
 }

 public interface Categories extends JpaRepository<Category, Long> {
  List<Category> findByActiveTrueOrderByName();
 }
  public interface HighlightTags extends JpaRepository<HighlightTag, Long> { List<HighlightTag> findAllByOrderByNameAsc(); }
  public interface SpecialDates extends CoupleScopedRepository<SpecialDate> { List<SpecialDate> findAllByCoupleIdOrderByDateAscLabelAscIdAsc(java.util.UUID coupleId); }
   public interface SpecialDateOccurrences extends CoupleScopedRepository<SpecialDateOccurrence> {
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) Optional<SpecialDateOccurrence> findBySpecialDateIdAndOccurredOnAndCoupleId(Long specialDateId, LocalDate occurredOn, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findBySpecialDateIdInAndOccurredOnBetweenAndCoupleId(Collection<Long> specialDateIds, LocalDate from, LocalDate to, java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findAllByCoupleIdOrderByOccurredOnDescIdDesc(java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"specialDate", "createdBy", "updatedBy"}) List<SpecialDateOccurrence> findByCoupleIdAndOccurredOnLessThanEqualOrderByOccurredOnDescIdDesc(java.util.UUID coupleId, LocalDate occurredOn);
    }
  public interface SpecialDateOccurrenceComments extends CoupleScopedRepository<SpecialDateOccurrenceComment> {
   @EntityGraph(attributePaths = {"author", "updatedBy"}) List<SpecialDateOccurrenceComment> findByOccurrenceIdOrderByAuthorUsername(Long occurrenceId);
   @EntityGraph(attributePaths = {"author", "updatedBy"}) Optional<SpecialDateOccurrenceComment> findByOccurrenceIdAndAuthorId(Long occurrenceId, Long authorId);
  }
  public interface SpecialDateOccurrencePhotos extends CoupleScopedRepository<SpecialDateOccurrencePhoto> {
   @EntityGraph(attributePaths = {"occurrence", "occurrence.specialDate", "createdBy"}) List<SpecialDateOccurrencePhoto> findByOccurrenceIdOrderByPositionAscIdAsc(Long occurrenceId);
   @EntityGraph(attributePaths = {"occurrence", "occurrence.specialDate", "createdBy"}) Optional<SpecialDateOccurrencePhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   long countByOccurrenceId(Long occurrenceId);
  }
  public interface Settings extends JpaRepository<GlobalSettings, Integer> {
   @Modifying @Query(value = "insert into global_settings (id, catalog_page_size) values (1, 5) on conflict (id) do nothing", nativeQuery = true) int insertDefaultIfMissing();
  }

  public interface Places extends CoupleScopedRepository<Place> {
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) List<Place> findAllByCoupleId(java.util.UUID coupleId);
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) @Query("select p from Place p where p.id=:id and p.coupleId=:coupleId") Optional<Place> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
  boolean existsByCategoryId(Long categoryId);
  boolean existsByHighlightTagsId(Long tagId);
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
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findAllByCoupleId(java.util.UUID coupleId);
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByPlaceIdOrderByVisitedOnDescIdDesc(Long placeId);
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(Collection<Long> placeIds);
        @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByCoupleIdAndVisitedOnLessThanEqualOrderByVisitedOnDescIdDesc(java.util.UUID coupleId, LocalDate visitedOn);
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findByPlaceIdAndVisitedOn(Long placeId, LocalDate visitedOn);
      boolean existsByPlaceId(Long placeId);
    @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   }

    public interface PlaceVisitPhotos extends CoupleScopedRepository<PlaceVisitPhoto> {
     @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdOrderByPositionAscIdAsc(Long visitId);
     @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdInOrderByVisitIdAscPositionAscIdAsc(Collection<Long> visitIds);
    @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) Optional<PlaceVisitPhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    long countByVisitId(Long visitId);
   }

   public interface PlaceVisitReviews extends CoupleScopedRepository<PlaceVisitReview> {
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdOrderByAuthorUsername(Long visitId);
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdInOrderByVisitIdAscAuthorUsername(Collection<Long> visitIds);
    @Query("select r.id as reviewId, author.username as author from PlaceVisitReview r join r.author author where r.visit.id in :visitIds") List<ReviewAuthor> authorsByVisitIdIn(@Param("visitIds") Collection<Long> visitIds);
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) Optional<PlaceVisitReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<PlaceVisitReview> findByVisitIdAndAuthorId(Long visitId, Long authorId);
   }

   public interface Items extends CoupleScopedRepository<Item> {
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"}) Optional<Item> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
   @EntityGraph(attributePaths = {"createdBy", "reviews", "reviews.author"}) List<Item> findByVisitIdAndDeletedAtIsNullOrderByIdDesc(Long visitId);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.deletedAt is null order by i.id desc")
    List<Item> findCatalogByPlaceId(@Param("placeId") Long placeId);
     @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place"})
     @Query("select i from Item i where i.visit.place.id = :placeId and i.deletedAt is null order by i.id desc")
     List<Item> findCatalogByPlaceId(@Param("placeId") Long placeId, Pageable pageable);
   @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"})
   @Query("select i from Item i where i.visit.place.id = :placeId and i.visit.visitedOn = :visitDate and i.deletedAt is null order by i.id desc")
    List<Item> findCatalogByPlaceIdAndVisitDate(@Param("placeId") Long placeId, @Param("visitDate") LocalDate visitDate);
     @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place"})
     @Query("select i from Item i where i.visit.place.id = :placeId and i.visit.visitedOn = :visitDate and i.deletedAt is null order by i.id desc")
     List<Item> findCatalogByPlaceIdAndVisitDate(@Param("placeId") Long placeId, @Param("visitDate") LocalDate visitDate, Pageable pageable);
   @Query("select distinct i.visit.visitedOn from Item i where i.visit.place.id = :placeId and i.deletedAt is null order by i.visit.visitedOn desc") List<LocalDate> findItemDatesByPlaceId(@Param("placeId") Long placeId);
   @Query("select i.visit.place.id as placeId, count(distinct i) as itemCount, coalesce(avg(review.taste), 0.0) as tasteAverage, coalesce(avg(review.price), 0.0) as priceAverage from Item i left join i.reviews review where i.visit.place.id in :ids and i.deletedAt is null group by i.visit.place.id") List<PlaceMetric> metrics(@Param("ids") Collection<Long> ids);
  }

  public interface Photos extends CoupleScopedRepository<ItemPhoto> {
  Optional<ItemPhoto> findByItemId(Long id);
  @EntityGraph(attributePaths = "item") List<ItemPhoto> findByItemIdIn(Collection<Long> ids);
 }

  public interface PlaceReviews extends CoupleScopedRepository<PlaceReview> {
   @Query("select r.place.id as placeId, author.username as author, r.comment as comment, r.location as location, r.heating as heating, r.bathrooms as bathrooms, r.exterior as exterior, r.seating as seating, r.service as service, r.ambiance as ambiance from PlaceReview r join r.author author where r.place.id in :placeIds order by r.place.id, author.username") List<PlaceReviewSummary> summariesByPlaceIdIn(@Param("placeIds") Collection<Long> placeIds);
   Optional<PlaceReview> findByPlaceIdAndAuthorId(Long placeId, Long authorId);
   @Query("select r.place.id as placeId, avg((coalesce(r.location, 0) + coalesce(r.heating, 0) + coalesce(r.bathrooms, 0) + coalesce(r.exterior, 0) + coalesce(r.seating, 0) + coalesce(r.service, 0) + coalesce(r.ambiance, 0)) / (case when r.location is null then 0 else 1 end + case when r.heating is null then 0 else 1 end + case when r.bathrooms is null then 0 else 1 end + case when r.exterior is null then 0 else 1 end + case when r.seating is null then 0 else 1 end + case when r.service is null then 0 else 1 end + case when r.ambiance is null then 0 else 1 end)) as venueAverage from PlaceReview r where r.place.id in :ids group by r.place.id") List<VenueMetric> venueMetrics(@Param("ids") Collection<Long> ids);
 }

   public interface PlacePhotos extends CoupleScopedRepository<PlacePhoto> {
   Optional<PlacePhoto> findByPlaceId(Long placeId);
   @EntityGraph(attributePaths = "place") List<PlacePhoto> findByPlaceIdIn(Collection<Long> placeIds);
  }

  public interface FilmPhotos extends CoupleScopedRepository<FilmPhoto> {
   Optional<FilmPhoto> findByFilmId(Long filmId);
   @Query("select p.id as id, p.film.id as filmId, p.width as width, p.height as height, p.createdAt as createdAt from FilmPhoto p where p.film.id in :filmIds") List<FilmPhotoMetadata> metadataByFilmIdIn(@Param("filmIds") Collection<Long> filmIds);
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
   List<FilmGenreOption> findAllByOrderByNameAsc();
   List<FilmGenreOption> findAllByNameIn(Collection<String> names);
  }

   public interface ItemReviews extends CoupleScopedRepository<ItemReview> {
     @EntityGraph(attributePaths = {"item", "author"}) List<ItemReview> findByItemIdInOrderByItemIdAscAuthorUsername(Collection<Long> itemIds);
     Optional<ItemReview> findByItemIdAndAuthorId(Long itemId, Long authorId);
    @Query("select r.id as reviewId, author.username as author from ItemReview r join r.author author where r.item.id in :itemIds") List<ReviewAuthor> authorsByItemIdIn(@Param("itemIds") Collection<Long> itemIds);
  }

  public interface Films extends CoupleScopedRepository<Film> {
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) List<Film> findAllByCoupleId(java.util.UUID coupleId);
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) @Query("select f from Film f where f.id=:id and f.coupleId=:coupleId") Optional<Film> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
  Optional<Film> findByTmdbIdAndCoupleId(Long tmdbId, java.util.UUID coupleId);
  boolean existsByPlatformId(Long platformId);
  }

  public interface FilmRating { Long getFilmId(); Double getRating(); }

     public interface FilmReviews extends CoupleScopedRepository<FilmReview> {
    @EntityGraph(attributePaths = {"author", "metrics", "view"}) @Query("select r from FilmReview r where r.film.id=:filmId order by r.view.watchedOn desc, r.id desc") List<FilmReview> findByFilmIdOrderByViewWatchedOnDescIdDesc(@Param("filmId") Long filmId);
     @Query("select r.id as reviewId, author.username as author from FilmReview r join r.author author where r.film.id=:filmId") List<ReviewAuthor> authorsByFilmId(@Param("filmId") Long filmId);
     @Query("select r.film.id as filmId, avg(r.rating) as rating from FilmReview r where r.film.id in :filmIds group by r.film.id") List<FilmRating> ratingsByFilmIdIn(@Param("filmIds") Collection<Long> filmIds);
     @EntityGraph(attributePaths = {"author", "metrics", "view", "film"}) Optional<FilmReview> findByIdAndFilmIdAndCoupleId(Long id, Long filmId, java.util.UUID coupleId);
    boolean existsByViewIdAndAuthorId(Long viewId, Long authorId);
  }

     public interface FilmViews extends CoupleScopedRepository<FilmView> {
       @EntityGraph(attributePaths = {"film", "createdBy", "updatedBy"}) List<FilmView> findAllByCoupleId(java.util.UUID coupleId);
       @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) List<FilmView> findByFilmIdOrderByWatchedOnDescIdDesc(Long filmId);
       @EntityGraph(attributePaths = {"film", "film.platform", "film.genres", "createdBy", "updatedBy"}) List<FilmView> findByCoupleIdAndWatchedOnLessThanEqualOrderByWatchedOnDescIdDesc(java.util.UUID coupleId, LocalDate watchedOn);
      @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) Optional<FilmView> findByIdAndFilmIdAndCoupleId(Long id, Long filmId, java.util.UUID coupleId);
      Optional<FilmView> findByFilmIdAndWatchedOnAndCoupleId(Long filmId, LocalDate watchedOn, java.util.UUID coupleId);
   }

  public interface HomeRecipes extends CoupleScopedRepository<HomeRecipe> {
    @EntityGraph(attributePaths = {"author", "ingredients", "steps", "repeatedFrom"}) List<HomeRecipe> findByHomeOrderByPreparedOnDescIdDesc(Home home);
    @EntityGraph(attributePaths = {"author", "ingredients", "steps", "repeatedFrom"}) Optional<HomeRecipe> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"author"}) List<HomeRecipe> findByRepeatedFromIdOrderByPreparedOnDescIdDesc(Long repeatedFromId);
    boolean existsByRepeatedFromId(Long repeatedFromId);
  }

  public interface HomeRecipePhotos extends CoupleScopedRepository<HomeRecipePhoto> {
   Optional<HomeRecipePhoto> findByRecipeId(Long recipeId);
   @EntityGraph(attributePaths = "recipe") List<HomeRecipePhoto> findByRecipeIdIn(Collection<Long> recipeIds);
  }

  public interface HomeRecipeReviews extends CoupleScopedRepository<HomeRecipeReview> {
   @EntityGraph(attributePaths = {"author", "recipe"}) @Query("select r from HomeRecipeReview r where r.recipe.id in :recipeIds order by r.recipe.id, r.author.username") List<HomeRecipeReview> findByRecipeIdInOrderByAuthorUsername(@Param("recipeIds") Collection<Long> recipeIds);
   @EntityGraph(attributePaths = "author") Optional<HomeRecipeReview> findByRecipeIdAndAuthorId(Long recipeId, Long authorId);
  }

  public interface WhyFunCategories extends JpaRepository<WhyFunCategory, Long> {
   @EntityGraph(attributePaths = "parent") List<WhyFunCategory> findAllByOrderByParentIdAscNameAsc();
   @EntityGraph(attributePaths = "parent") Optional<WhyFunCategory> findDetailedById(Long id);
   Optional<WhyFunCategory> findByParentIsNullAndSlug(String slug);
   Optional<WhyFunCategory> findByParentIdAndSlug(Long parentId, String slug);
   boolean existsByParentId(Long parentId);
  }

  public interface WhyFunVenues extends CoupleScopedRepository<WhyFunVenue> {
     @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "schedules"}) List<WhyFunVenue> findAllByCoupleId(java.util.UUID coupleId);
   @Query("select v from WhyFunVenue v join fetch v.category join fetch v.subcategory join fetch v.createdBy where v.coupleId = :coupleId and (:categoryId is null or v.category.id = :categoryId) and (:subcategoryId is null or v.subcategory.id = :subcategoryId) and (:cursor is null or v.id < :cursor) order by v.id desc") List<WhyFunVenue> list(@Param("coupleId") java.util.UUID coupleId, @Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId, @Param("cursor") Long cursor, Pageable pageable);
     @EntityGraph(attributePaths = {"category", "subcategory", "createdBy", "schedules"}) @Query("select v from WhyFunVenue v where v.id=:id and v.coupleId=:coupleId") Optional<WhyFunVenue> findDetailedByIdAndCoupleId(@Param("id") Long id, @Param("coupleId") java.util.UUID coupleId);
   long countBySubcategoryId(Long subcategoryId);
   boolean existsByCategoryIdOrSubcategoryId(Long categoryId, Long subcategoryId);
  }

     public interface WhyFunVenuePhotos extends CoupleScopedRepository<WhyFunVenuePhoto> {
    @EntityGraph(attributePaths = "venue") List<WhyFunVenuePhoto> findByVenueIdInOrderByVenueIdAscIdAsc(Collection<Long> venueIds);
    List<WhyFunVenuePhoto> findByVenueIdOrderByIdAsc(Long venueId);
    @EntityGraph(attributePaths = {"venue", "venue.createdBy"}) Optional<WhyFunVenuePhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
   Optional<WhyFunVenuePhoto> findByIdAndVenueIdAndCoupleId(Long id, Long venueId, java.util.UUID coupleId);
     @Query("select p.id as id, p.width as width, p.height as height, p.createdAt as createdAt from WhyFunVenuePhoto p where p.id in :photoIds") List<PhotoMetadata> metadataByIdIn(@Param("photoIds") Collection<Long> photoIds);
    long countByVenueId(Long venueId);
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
    @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id in :venueIds order by r.venue.id asc, u.username") List<WhyFunReviewSummary> summariesByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
    @EntityGraph(attributePaths = "author") Optional<WhyFunVenueReview> findByVenueIdAndAuthorId(Long venueId, Long authorId);
   }

    public interface WhyFunVisits extends CoupleScopedRepository<WhyFunVisit> {
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "createdBy", "updatedBy"}) List<WhyFunVisit> findByVenueIdOrderByScheduledAtDescIdDesc(Long venueId);
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.schedules", "createdBy", "updatedBy"}) Optional<WhyFunVisit> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "createdBy", "updatedBy"}) List<WhyFunVisit> findAllByCoupleId(java.util.UUID coupleId);
      @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "createdBy", "updatedBy"}) List<WhyFunVisit> findByCoupleIdAndScheduledAtLessThanEqualOrderByScheduledAtDescIdDesc(java.util.UUID coupleId, LocalDate scheduledAt);
     @Query("select v.venue.id as activityId, count(v) as visitCount from WhyFunVisit v where v.venue.id in :activityIds group by v.venue.id") List<ActivityVisitCount> countsByActivityIdIn(@Param("activityIds") Collection<Long> activityIds);
   }

   public interface WhyFunVisitPhotos extends CoupleScopedRepository<WhyFunVisitPhoto> {
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) List<WhyFunVisitPhoto> findByVisitIdOrderByPositionAscIdAsc(Long visitId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) Optional<WhyFunVisitPhoto> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    long countByVisitId(Long visitId);
   }

   public interface WhyFunVisitReviews extends CoupleScopedRepository<WhyFunVisitReview> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<WhyFunVisitReview> findByVisitIdOrderByAuthorUsername(Long visitId);
    @Query("select r.visit.venue.id as activityId, avg(r.rating) as rating from WhyFunVisitReview r where r.visit.venue.id in :activityIds group by r.visit.venue.id") List<ActivityRating> ratingsByActivityIdIn(@Param("activityIds") Collection<Long> activityIds);
    @Query("select r.id as reviewId, author.username as author from WhyFunVisitReview r join r.author author where r.visit.id=:visitId") List<ReviewAuthor> authorsByVisitId(@Param("visitId") Long visitId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "author", "updatedBy"}) Optional<WhyFunVisitReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<WhyFunVisitReview> findByVisitIdAndAuthorId(Long visitId, Long authorId);
   }

   public interface Recipes extends CoupleScopedRepository<Recipe> {
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) Optional<Recipe> findByIdAndCoupleId(Long id, java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) List<Recipe> findAllByCoupleId(java.util.UUID coupleId);
   }

    public interface RecipePhotos extends CoupleScopedRepository<RecipePhoto> {
     Optional<RecipePhoto> findByRecipeId(Long recipeId);
     @Query("select p.id as id, p.recipe.id as recipeId, p.width as width, p.height as height, p.createdAt as createdAt from RecipePhoto p where p.recipe.id in :recipeIds") List<RecipePhotoMetadata> metadataByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
    }

    public interface RecipePhotoMetadata extends PhotoMetadata { Long getRecipeId(); }

    public interface RecipeCookingCount { Long getRecipeId(); Long getCookingCount(); }
    public interface RecipeHome { Long getRecipeId(); Home getHome(); }
    public interface RecipeRating { Long getRecipeId(); Double getRating(); }

    public interface Cookings extends CoupleScopedRepository<Cooking> {
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findAllByCoupleId(java.util.UUID coupleId);
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByCoupleIdAndHomeOrderByCookedOnDescIdDesc(java.util.UUID coupleId, Home home);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByRecipeIdAndCoupleIdOrderByCookedOnDescIdDesc(Long recipeId, java.util.UUID coupleId);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByCoupleIdAndCookedOnLessThanEqualOrderByCookedOnDescIdDesc(java.util.UUID coupleId, LocalDate cookedOn);
     @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) Optional<Cooking> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
     boolean existsByRecipeId(Long recipeId);
     @Query("select c.recipe.id as recipeId, count(c) as cookingCount from Cooking c where c.recipe.id in :recipeIds group by c.recipe.id") List<RecipeCookingCount> cookingCountsByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
     @Query("select distinct c.recipe.id as recipeId, c.home as home from Cooking c where c.recipe.id in :recipeIds") List<RecipeHome> homesByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
   }

   public interface CookingReviews extends CoupleScopedRepository<CookingReview> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<CookingReview> findByCookingIdOrderByAuthorUsername(Long cookingId);
     @Query("select r.id as reviewId, author.username as author from CookingReview r join r.author author where r.cooking.id=:cookingId") List<ReviewAuthor> authorsByCookingId(@Param("cookingId") Long cookingId);
     @Query("select r.cooking.recipe.id as recipeId, avg(r.rating) as rating from CookingReview r where r.cooking.recipe.id in :recipeIds group by r.cooking.recipe.id") List<RecipeRating> ratingsByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
    @EntityGraph(attributePaths = {"cooking", "cooking.recipe", "author", "updatedBy"}) Optional<CookingReview> findDetailedByIdAndCoupleId(Long id, java.util.UUID coupleId);
    Optional<CookingReview> findByCookingIdAndAuthorId(Long cookingId, Long authorId);
   }
}
