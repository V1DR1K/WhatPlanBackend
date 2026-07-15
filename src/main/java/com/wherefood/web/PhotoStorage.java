package com.wherefood.web;

import com.wherefood.domain.*;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.Base64;

@Service
public class PhotoStorage {
 public ItemPhoto store(Item item, MultipartFile upload) throws IOException {
  ImageData data = imageData(upload);
  ItemPhoto photo = new ItemPhoto();
  photo.item = item; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
  return photo;
 }
 public PlacePhoto store(Place place, MultipartFile upload) throws IOException {
  ImageData data = imageData(upload);
  PlacePhoto photo = new PlacePhoto();
  photo.place = place; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
  return photo;
 }
 private ImageData imageData(MultipartFile upload) throws IOException {
  if (upload.getSize() > 10 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Máximo 10 MB");
  BufferedImage image = ImageIO.read(upload.getInputStream());
  if (image == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida");
  return new ImageData(Base64.getEncoder().encodeToString(render(image, 1600)),Base64.getEncoder().encodeToString(render(image, 480)),image.getWidth(),image.getHeight());
 }

 private byte[] render(BufferedImage image, int max) throws IOException {
  var out = new ByteArrayOutputStream();
  Thumbnails.of(image).size(max, max).outputFormat("webp").outputQuality(.82).toOutputStream(out);
  return out.toByteArray();
 }

 public String url(String base64) { return base64 == null ? null : "data:image/webp;base64," + base64; }
 private record ImageData(String image,String thumbnail,int width,int height) {}
}
