package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhyFunApiTest {
 @Test
  void separatesUpcomingAndPastPlans() throws Exception {
   Method matcher = WhyFunApi.class.getDeclaredMethod("matchesTimeline", WhyFunVenue.class, String.class, LocalDate.class);
  matcher.setAccessible(true);
  WhyFunVenue plan = new WhyFunVenue();
   LocalDate now = LocalDate.of(2026, 7, 22);
   plan.scheduledAt = now.plusDays(1);
  assertTrue((Boolean) matcher.invoke(null, plan, "UPCOMING", now));
   assertFalse((Boolean) matcher.invoke(null, plan, "PAST", now));
  }

  @Test
  void exposesTheParentActivityProfilePhoto() {
   WhyFunVenues activities = mock(WhyFunVenues.class); WhyFunVenuePhotos photos = mock(WhyFunVenuePhotos.class);
   User tomas = new User(); tomas.username = "tomas";
   WhyFunCategory category = new WhyFunCategory(); category.id = 1L; category.name = "Arte"; category.slug = "arte"; category.icon = "a";
   WhyFunVenue activity = new WhyFunVenue(); activity.id = 4L; activity.name = "Museo"; activity.address = "Centro"; activity.category = activity.subcategory = category; activity.createdBy = activity.updatedBy = tomas; activity.coverPhotoId = 9L;
   when(activities.findDetailedById(4L)).thenReturn(Optional.of(activity)); when(photos.metadataByIdIn(any())).thenReturn(List.of(photo(9L, 1000, 700)));

   ActivityDto result = new WhyFunActivityApi(null, activities, photos, null, null, null, null).getActivity(4L);

   assertEquals("/why-fun/activities/4/photo?v=9", result.profilePhoto().url());
   assertEquals("/why-fun/activities/4/photo?thumbnail=true&v=9", result.profilePhoto().thumbnailUrl());
  }

  @Test
  void paginatesActivitiesWithSearchVisitAndRatingFilters() {
   WhyFunVenues activities = mock(WhyFunVenues.class); WhyFunVenuePhotos photos = mock(WhyFunVenuePhotos.class); WhyFunVisits visits = mock(WhyFunVisits.class); WhyFunVisitReviews reviews = mock(WhyFunVisitReviews.class);
   User tomas = new User(); tomas.username = "tomas";
   WhyFunCategory category = category(1L, "Arte"); WhyFunCategory subcategory = category(2L, "Museos"); subcategory.parent = category;
   WhyFunVenue best = activity(1L, "Museo de arte", category, subcategory, tomas, "2026-07-23T00:00:00Z");
   WhyFunVenue other = activity(2L, "Museo historico", category, subcategory, tomas, "2026-07-22T00:00:00Z");
    WhyFunVenue pending = activity(3L, "Archivo de arte", category, subcategory, tomas, "2026-07-21T00:00:00Z");
   when(activities.findAll()).thenReturn(List.of(best, other, pending));
   when(reviews.ratingsByActivityIdIn(any())).thenReturn(List.of(rating(1L, 5.0), rating(2L, 3.0)));
   when(visits.countsByActivityIdIn(any())).thenReturn(List.of(count(1L, 2L), count(2L, 1L)));

   WhyFunActivityApi api = new WhyFunActivityApi(null, activities, photos, visits, null, reviews, null);
   Slice<ActivityDto> first = api.listActivities(1L, 2L, "museo", true, "rating-desc", null, 1);
    Slice<ActivityDto> second = api.listActivities(1L, 2L, "museo", true, "rating-desc", first.nextCursor(), 1);
    Slice<ActivityDto> unvisited = api.listActivities(1L, 2L, null, false, "date-desc", null, 5);
    Slice<ActivityDto> defaultOrder = api.listActivities(1L, 2L, null, null, null, null, 5);

   assertEquals(List.of(1L), first.content().stream().map(ActivityDto::id).toList());
   assertEquals(1L, first.nextCursor());
   assertEquals(2L, first.content().getFirst().visitCount());
   assertEquals(List.of(2L), second.content().stream().map(ActivityDto::id).toList());
    assertEquals(null, second.nextCursor());
    assertEquals(List.of(3L), unvisited.content().stream().map(ActivityDto::id).toList());
    assertEquals(List.of(1L, 2L, 3L), defaultOrder.content().stream().map(ActivityDto::id).toList());
  }

  private static WhyFunCategory category(Long id, String name) { WhyFunCategory category = new WhyFunCategory(); category.id = id; category.name = name; category.slug = name.toLowerCase(); category.icon = "x"; return category; }
  private static WhyFunVenue activity(Long id, String name, WhyFunCategory category, WhyFunCategory subcategory, User author, String createdAt) { WhyFunVenue activity = new WhyFunVenue(); activity.id = id; activity.name = name; activity.address = "Centro"; activity.category = category; activity.subcategory = subcategory; activity.createdBy = activity.updatedBy = author; activity.createdAt = activity.updatedAt = Instant.parse(createdAt); return activity; }
  private static PhotoMetadata photo(Long id, Integer width, Integer height) { return new PhotoMetadata() { public Long getId() { return id; } public Integer getWidth() { return width; } public Integer getHeight() { return height; } public Instant getCreatedAt() { return Instant.parse("2026-07-23T00:00:00Z"); } }; }
  private static ActivityRating rating(Long activityId, Double rating) { return new ActivityRating() { public Long getActivityId() { return activityId; } public Double getRating() { return rating; } }; }
  private static ActivityVisitCount count(Long activityId, Long visitCount) { return new ActivityVisitCount() { public Long getActivityId() { return activityId; } public Long getVisitCount() { return visitCount; } }; }
}
