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
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByPlaceIdOrderByVisitedOnDescIdDesc(Long placeId);
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) List<PlaceVisit> findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(Collection<Long> placeIds);
       @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findByPlaceIdAndVisitedOn(Long placeId, LocalDate visitedOn);
      boolean existsByPlaceId(Long placeId);
    @EntityGraph(attributePaths = {"place", "createdBy", "updatedBy"}) Optional<PlaceVisit> findDetailedById(Long id);
   }

    public interface PlaceVisitPhotos extends JpaRepository<PlaceVisitPhoto, Long> {
     @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdOrderByPositionAscIdAsc(Long visitId);
     @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) List<PlaceVisitPhoto> findByVisitIdInOrderByVisitIdAscPositionAscIdAsc(Collection<Long> visitIds);
    @EntityGraph(attributePaths = {"visit", "visit.place", "createdBy"}) Optional<PlaceVisitPhoto> findDetailedById(Long id);
    long countByVisitId(Long visitId);
   }

    public interface PlaceVisitReviews extends JpaRepository<PlaceVisitReview, Long> {
     @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdOrderByAuthorUsername(Long visitId);
     @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) List<PlaceVisitReview> findByVisitIdInOrderByVisitIdAscAuthorUsername(Collection<Long> visitIds);
    @EntityGraph(attributePaths = {"visit", "visit.place", "author", "updatedBy"}) Optional<PlaceVisitReview> findDetailedById(Long id);
    Optional<PlaceVisitReview> findByVisitIdAndAuthorId(Long visitId, Long authorId);
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
   @EntityGraph(attributePaths = {"place", "author"}) List<PlaceReview> findByPlaceIdOrderByAuthorUsername(Long placeId);
   @Query("select r from PlaceReview r join fetch r.place join fetch r.author where r.place.id in :placeIds order by r.place.id, r.author.username") List<PlaceReview> findByPlaceIdInOrderByPlaceIdAscAuthorUsername(@Param("placeIds") Collection<Long> placeIds);
   Optional<PlaceReview> findByPlaceIdAndAuthorId(Long placeId, Long authorId);
   @Query("select r.place.id as placeId, avg((coalesce(r.location, 0) + coalesce(r.heating, 0) + coalesce(r.bathrooms, 0) + coalesce(r.exterior, 0) + coalesce(r.seating, 0) + coalesce(r.service, 0) + coalesce(r.ambiance, 0)) / (case when r.location is null then 0 else 1 end + case when r.heating is null then 0 else 1 end + case when r.bathrooms is null then 0 else 1 end + case when r.exterior is null then 0 else 1 end + case when r.seating is null then 0 else 1 end + case when r.service is null then 0 else 1 end + case when r.ambiance is null then 0 else 1 end)) as venueAverage from PlaceReview r where r.place.id in :ids group by r.place.id") List<VenueMetric> venueMetrics(@Param("ids") Collection<Long> ids);
 }

   public interface PlacePhotos extends JpaRepository<PlacePhoto, Long> {
   Optional<PlacePhoto> findByPlaceId(Long placeId);
   @EntityGraph(attributePaths = "place") List<PlacePhoto> findByPlaceIdIn(Collection<Long> placeIds);
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
     @EntityGraph(attributePaths = {"author", "metrics", "view", "film"}) Optional<FilmReview> findByIdAndFilmId(Long id, Long filmId);
    boolean existsByViewIdAndAuthorId(Long viewId, Long authorId);
  }

    public interface FilmViews extends JpaRepository<FilmView, Long> {
      @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) List<FilmView> findByFilmIdOrderByWatchedOnDescIdDesc(Long filmId);
      @EntityGraph(attributePaths = {"createdBy", "updatedBy"}) Optional<FilmView> findByIdAndFilmId(Long id, Long filmId);
      Optional<FilmView> findByFilmIdAndWatchedOn(Long filmId, LocalDate watchedOn);
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
    Optional<WhyFunVenuePhoto> findByIdAndVenueId(Long id, Long venueId);
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

   public interface WhyFunVisits extends JpaRepository<WhyFunVisit, Long> {
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "createdBy", "updatedBy"}) List<WhyFunVisit> findByVenueIdOrderByScheduledAtDescIdDesc(Long venueId);
    @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "venue.schedules", "createdBy", "updatedBy"}) Optional<WhyFunVisit> findDetailedById(Long id);
    @Override @EntityGraph(attributePaths = {"venue", "venue.category", "venue.subcategory", "createdBy", "updatedBy"}) List<WhyFunVisit> findAll();
   }

   public interface WhyFunVisitPhotos extends JpaRepository<WhyFunVisitPhoto, Long> {
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) List<WhyFunVisitPhoto> findByVisitIdOrderByPositionAscIdAsc(Long visitId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "createdBy"}) Optional<WhyFunVisitPhoto> findDetailedById(Long id);
    long countByVisitId(Long visitId);
   }

   public interface WhyFunVisitReviews extends JpaRepository<WhyFunVisitReview, Long> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<WhyFunVisitReview> findByVisitIdOrderByAuthorUsername(Long visitId);
    @EntityGraph(attributePaths = {"visit", "visit.venue", "author", "updatedBy"}) Optional<WhyFunVisitReview> findDetailedById(Long id);
    Optional<WhyFunVisitReview> findByVisitIdAndAuthorId(Long visitId, Long authorId);
   }

   public interface Recipes extends JpaRepository<Recipe, Long> {
    @Override @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) Optional<Recipe> findById(Long id);
    @Override @EntityGraph(attributePaths = {"createdBy", "updatedBy", "ingredients", "steps"}) List<Recipe> findAll();
   }

    public interface RecipePhotos extends JpaRepository<RecipePhoto, Long> {
     Optional<RecipePhoto> findByRecipeId(Long recipeId);
    }

   public interface Cookings extends JpaRepository<Cooking, Long> {
    @Override @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findAll();
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByHomeOrderByCookedOnDescIdDesc(Home home);
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) List<Cooking> findByRecipeIdOrderByCookedOnDescIdDesc(Long recipeId);
    @EntityGraph(attributePaths = {"recipe", "recipe.ingredients", "recipe.steps", "createdBy", "updatedBy"}) Optional<Cooking> findDetailedById(Long id);
    boolean existsByRecipeId(Long recipeId);
   }

   public interface CookingReviews extends JpaRepository<CookingReview, Long> {
    @EntityGraph(attributePaths = {"author", "updatedBy"}) List<CookingReview> findByCookingIdOrderByAuthorUsername(Long cookingId);
    @EntityGraph(attributePaths = {"cooking", "cooking.recipe", "author", "updatedBy"}) Optional<CookingReview> findDetailedById(Long id);
    Optional<CookingReview> findByCookingIdAndAuthorId(Long cookingId, Long authorId);
   }
}
