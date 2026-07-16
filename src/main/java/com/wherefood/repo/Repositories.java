package com.wherefood.repo;

import com.wherefood.domain.*;
import java.time.LocalDate;
import java.util.*;
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

 public interface Items extends JpaRepository<Item, Long> {
  @Override @EntityGraph(attributePaths = {"author", "place"}) Optional<Item> findById(Long id);
  @EntityGraph(attributePaths = "author") List<Item> findByPlaceIdAndVisitDateAndDeletedAtIsNullOrderByIdDesc(Long placeId, LocalDate visitDate);
  @Query("select distinct i.visitDate from Item i where i.place.id=:placeId and i.deletedAt is null order by i.visitDate desc") List<LocalDate> findActiveVisitDates(@Param("placeId") Long placeId);
  @Query("select i.place.id as placeId, count(i) as itemCount, avg(i.taste) as tasteAverage, avg(i.price) as priceAverage from Item i where i.place.id in :ids and i.deletedAt is null group by i.place.id") List<PlaceMetric> metrics(@Param("ids") Collection<Long> ids);
 }

 public interface Photos extends JpaRepository<ItemPhoto, Long> {
  Optional<ItemPhoto> findByItemId(Long id);
  @EntityGraph(attributePaths = "item") List<ItemPhoto> findByItemIdIn(Collection<Long> ids);
 }

 public interface PlaceReviews extends JpaRepository<PlaceReview, Long> {
  @EntityGraph(attributePaths = "author") List<PlaceReview> findByPlaceIdOrderByAuthorUsername(Long placeId);
  Optional<PlaceReview> findByPlaceIdAndAuthorId(Long placeId, Long authorId);
  @Query("select r.place.id as placeId, avg((r.location + r.heating + r.bathrooms + r.exterior + r.seating + r.service + r.ambiance) / 7.0) as venueAverage from PlaceReview r where r.place.id in :ids group by r.place.id") List<VenueMetric> venueMetrics(@Param("ids") Collection<Long> ids);
 }

 public interface PlacePhotos extends JpaRepository<PlacePhoto, Long> {
  Optional<PlacePhoto> findByPlaceId(Long placeId);
 }
}
