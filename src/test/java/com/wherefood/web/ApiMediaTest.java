package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Item;
import com.wherefood.domain.ItemPhoto;
import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.Photos;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiMediaTest {
 @Test
 void identifiesWebpBeforeImageIoDecodesIt() {
  assertTrue(PhotoStorage.isWebp(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
  assertFalse(PhotoStorage.isWebp(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}));
 }

 @Test
 void servesTheRequestedItemPhotoVariantAsCacheableWebp() {
  Items items = mock(Items.class);
    Photos photos = mock(Photos.class);
    Item item = new Item();
    item.id = 42L;
    item.visit = new PlaceVisit();
    item.visit.place = new Place();
  ItemPhoto photo = new ItemPhoto();
  photo.imageBase64 = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
  photo.thumbnailBase64 = Base64.getEncoder().encodeToString(new byte[] {4, 5});
  when(items.findById(42L)).thenReturn(Optional.of(item));
  when(photos.findByItemId(42L)).thenReturn(Optional.of(photo));

   var response = new Api(null, null, null, null, null, items, photos, null, null, null, null, null, new PhotoStorage()).itemPhoto(42L, true);

  assertEquals("image/webp", response.getHeaders().getContentType().toString());
  assertArrayEquals(new byte[] {4, 5}, response.getBody());
 }
}
