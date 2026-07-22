package com.wherefood.repo;

import com.wherefood.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public final class Repositories {
 private Repositories() {}

 public interface Users extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
 }

 public interface Categories extends JpaRepository<Category, Long> {
  List<Category> findByActiveTrueOrderByName();
 }
 public interface HighlightTags extends JpaRepository<HighlightTag, Long> { List<HighlightTag> findAllByOrderByNameAsc(); }

 public interface Places extends JpaRepository<Place, Long> {
  @Override @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) List<Place> findAll();
  @EntityGraph(attributePaths = {"category", "createdBy", "highlightTags"}) @Query("select p from Place p where p.id=:id") Optional<Place> findDetailedById(@Param("id") Long id);
 }

 public interface PlaceMetric {
  Long getPlaceId(); Long getItemCount(); Double getTasteAverage(); Double getPriceAverage();
 }

 public interface VenueMetric {
  Long getPlaceId(); Double getVenueAverage();
 }

  public interface PlaceVisits extends JpaRepository<PlaceVisit, Long> {
     @EntityGraph(attributePaths = {"place", "createdBy"}) List<PlaceVisit> findByPlaceIdOrderByVisitedOnDescIdDesc(Long placeId);
     @EntityGraph(attributePaths = {"place", "createdBy"}) Optional<PlaceVisit> findByPlaceIdAndVisitedOn(Long placeId, LocalDate visitedOn);
   @EntityGraph(attributePaths = {"place", "createdBy"}) Optional<PlaceVisit> findDetailedById(Long id);
  }

  public interface Items extends JpaRepository<Item, Long> {
   @Override @EntityGraph(attributePaths = {"createdBy", "visit", "visit.place", "reviews", "reviews.author"}) Optional<Item> findById(Long id);
   @EntityGraph(attributePaths = {"createdBy", "reviews", "reviews.author"}) List<Item> findByVisitIdAndDeletedAtIsNullOrderByIdDesc(Long visitId);
   @Query("select i.visit.place.id as placeId, count(distinct i) as itemCount, coalesce(avg(review.taste), 0.0) as tasteAverage, coalesce(avg(review.price), 0.0) as priceAverage from Item i left join i.reviews review where i.visit.place.id in :ids and i.deletedAt is null group by i.visit.place.id") List<PlaceMetric> metrics(@Param("ids") Collection<Long> ids);
  }

  public interface Photos extends JpaRepository<ItemPhoto, Long> {
  Optional<ItemPhoto> findByItemId(Long id);
  @EntityGraph(attributePaths = "item") List<ItemPhoto> findByItemIdIn(Collection<Long> ids);
 }

 public interface PlaceReviews extends JpaRepository<PlaceReview, Long> {
  @EntityGraph(attributePaths = "author") List<PlaceReview> findByPlaceIdOrderByAuthorUsername(Long placeId);
  Optional<PlaceReview> findByPlaceIdAndAuthorId(Long placeId, Long authorId);
   @Query("select r.place.id as placeId, avg((coalesce(r.location, 0) + coalesce(r.heating, 0) + coalesce(r.bathrooms, 0) + coalesce(r.exterior, 0) + coalesce(r.seating, 0) + coalesce(r.service, 0) + coalesce(r.ambiance, 0)) / (case when r.location is null then 0 else 1 end + case when r.heating is null then 0 else 1 end + case when r.bathrooms is null then 0 else 1 end + case when r.exterior is null then 0 else 1 end + case when r.seating is null then 0 else 1 end + case when r.service is null then 0 else 1 end + case when r.ambiance is null then 0 else 1 end)) as venueAverage from PlaceReview r where r.place.id in :ids group by r.place.id") List<VenueMetric> venueMetrics(@Param("ids") Collection<Long> ids);
 }

  public interface PlacePhotos extends JpaRepository<PlacePhoto, Long> {
  Optional<PlacePhoto> findByPlaceId(Long placeId);
 }

 public interface FilmPhotos extends JpaRepository<FilmPhoto, Long> {
  Optional<FilmPhoto> findByFilmId(Long filmId);
 }

 public interface WatchPlatforms extends JpaRepository<WatchPlatform, Long> {
  List<WatchPlatform> findByActiveTrueOrderByNameAsc();
  List<WatchPlatform> findAllByOrderByNameAsc();
 }

 public interface FilmGenreOptions extends JpaRepository<FilmGenreOption, Long> {
   List<FilmGenreOption> findAllByOrderByNameAsc();
   List<FilmGenreOption> findAllByNameIn(Collection<String> names);
  }

  public interface ItemReviews extends JpaRepository<ItemReview, Long> {
   Optional<ItemReview> findByItemIdAndAuthorId(Long itemId, Long authorId);
  }

 public interface Films extends JpaRepository<Film, Long> {
  @Override @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) List<Film> findAll();
  @EntityGraph(attributePaths = {"platform", "createdBy", "genres"}) @Query("select f from Film f where f.id=:id") Optional<Film> findDetailedById(@Param("id") Long id);
  Optional<Film> findByTmdbId(Long tmdbId);
 }

  public interface FilmReviews extends JpaRepository<FilmReview, Long> {
    @EntityGraph(attributePaths = {"author", "metrics", "view"}) @Query("select r from FilmReview r where r.film.id=:filmId order by r.view.watchedOn desc, r.id desc") List<FilmReview> findByFilmIdOrderByViewWatchedOnDescIdDesc(@Param("filmId") Long filmId);
    @EntityGraph(attributePaths = {"author", "metrics", "view"}) Optional<FilmReview> findByIdAndFilmId(Long id, Long filmId);
    boolean existsByViewIdAndAuthorId(Long viewId, Long authorId);
  }

  public interface FilmViews extends JpaRepository<FilmView, Long> {
    List<FilmView> findByFilmIdOrderByWatchedOnDescWatchedAtDescIdDesc(Long filmId);
    Optional<FilmView> findByIdAndFilmId(Long id, Long filmId);
    Optional<FilmView> findByFilmIdAndWatchedOnAndWatchedAt(Long filmId, LocalDate watchedOn, LocalTime watchedAt);
  }

  public interface HomeRecipes extends JpaRepository<HomeRecipe, Long> {
    @EntityGraph(attributePaths = {"author", "ingredients", "steps", "repeatedFrom"}) List<HomeRecipe> findByHomeOrderByPreparedOnDescIdDesc(Home home);
    @Override @EntityGraph(attributePaths = {"author", "ingredients", "steps", "repeatedFrom"}) Optional<HomeRecipe> findById(Long id);
    @EntityGraph(attributePaths = {"author"}) List<HomeRecipe> findByRepeatedFromIdOrderByPreparedOnDescIdDesc(Long repeatedFromId);
    boolean existsByRepeatedFromId(Long repeatedFromId);
  }

  public interface HomeRecipePhotos extends JpaRepository<HomeRecipePhoto, Long> {
   Optional<HomeRecipePhoto> findByRecipeId(Long recipeId);
   @EntityGraph(attributePaths = "recipe") List<HomeRecipePhoto> findByRecipeIdIn(Collection<Long> recipeIds);
  }

  public interface HomeRecipeReviews extends JpaRepository<HomeRecipeReview, Long> {
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

  public interface WhyFunVenues extends JpaRepository<WhyFunVenue, Long> {
    @Override @EntityGraph(attributePaths = {"category", "subcategory", "createdBy"}) List<WhyFunVenue> findAll();
   @Query("select v from WhyFunVenue v join fetch v.category join fetch v.subcategory join fetch v.createdBy where (:categoryId is null or v.category.id = :categoryId) and (:subcategoryId is null or v.subcategory.id = :subcategoryId) and (:cursor is null or v.id < :cursor) order by v.id desc") List<WhyFunVenue> list(@Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId, @Param("cursor") Long cursor, Pageable pageable);
    @EntityGraph(attributePaths = {"category", "subcategory", "createdBy"}) @Query("select v from WhyFunVenue v where v.id=:id") Optional<WhyFunVenue> findDetailedById(@Param("id") Long id);
   long countBySubcategoryId(Long subcategoryId);
  }

  public interface WhyFunVenuePhotos extends JpaRepository<WhyFunVenuePhoto, Long> {
   @EntityGraph(attributePaths = "venue") List<WhyFunVenuePhoto> findByVenueIdInOrderByVenueIdAscIdAsc(Collection<Long> venueIds);
   List<WhyFunVenuePhoto> findByVenueIdOrderByIdAsc(Long venueId);
   @EntityGraph(attributePaths = {"venue", "venue.createdBy"}) Optional<WhyFunVenuePhoto> findDetailedById(Long id);
   long countByVenueId(Long venueId);
  }

  public interface WhyFunReviewSummary {
   Long getId(); Long getVenueId(); String getAuthor(); Short getRating(); String getComment(); java.time.Instant getUpdatedAt();
  }

  public interface WhyFunVenueReviews extends JpaRepository<WhyFunVenueReview, Long> {
   @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id=:venueId order by u.username") List<WhyFunReviewSummary> summariesByVenueId(@Param("venueId") Long venueId);
   @Query("select r.id as id, r.venue.id as venueId, u.username as author, r.rating as rating, r.comment as comment, r.updatedAt as updatedAt from WhyFunVenueReview r join r.author u where r.venue.id in :venueIds order by r.venue.id asc, u.username") List<WhyFunReviewSummary> summariesByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
   @EntityGraph(attributePaths = "author") Optional<WhyFunVenueReview> findByVenueIdAndAuthorId(Long venueId, Long authorId);
  }
}
