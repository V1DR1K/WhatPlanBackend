package com.wherefood.web;

import com.wherefood.domain.Item;
import com.wherefood.domain.ItemPhoto;
import com.wherefood.repo.Repositories.Items;
import com.wherefood.repo.Repositories.Photos;
import com.wherefood.repo.Repositories.Places;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Read route kept separate from the place route so proxies can retrieve item cards reliably. */
@RestController @RequestMapping("/api") public class ItemReadApi {
 private final Items items; private final Photos photos; private final Places places; private final PhotoStorage storage;
 public ItemReadApi(Items items,Photos photos,Places places,PhotoStorage storage){this.items=items;this.photos=photos;this.places=places;this.storage=storage;}
 @GetMapping("/items") Slice<ItemDto> list(@RequestParam Long placeId,@RequestParam(required=false) Long cursor,@RequestParam(defaultValue="12") int size){
  if(!places.existsById(placeId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lugar no encontrado"); int limit=Math.max(1,Math.min(size,30));
  List<Item> result=items.findByPlaceIdOrderByIdDesc(placeId).stream().filter(i->cursor==null||i.id<cursor).limit(limit+1).toList(); Long next=result.size()>limit?result.get(limit).id:null; List<Item> page=result.stream().limit(limit).toList();
  Map<Long,ItemPhoto> photoMap=photos.findByItemIdIn(page.stream().map(i->i.id).toList()).stream().filter(p->p.item!=null&&p.item.id!=null).collect(java.util.stream.Collectors.toMap(p->p.item.id,p->p,(first,ignored)->first));
  return new Slice<>(page.stream().map(i->{ItemPhoto photo=photoMap.get(i.id);return new ItemDto(i.id,i.name,i.comment,i.taste,i.price,i.author.username,photo==null?null:storage.url(photo.imageBase64),photo==null?null:storage.url(photo.thumbnailBase64),i.createdAt);}).toList(),next);
 }
}
