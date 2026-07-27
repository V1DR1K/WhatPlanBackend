package com.wherefood.web;

import com.wherefood.domain.*;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

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
  public FilmPhoto store(Film film, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   FilmPhoto photo = new FilmPhoto();
   photo.film = film; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
    return photo;
   }
  public RecipePhoto store(Recipe recipe, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   RecipePhoto photo = new RecipePhoto();
   photo.recipe = recipe; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
   public HomeRecipePhoto store(HomeRecipe recipe, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   HomeRecipePhoto photo = new HomeRecipePhoto();
   photo.recipe = recipe; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
  public WhyFunVenuePhoto store(WhyFunVenue venue, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   WhyFunVenuePhoto photo = new WhyFunVenuePhoto();
   photo.venue = venue; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
  public PlaceVisitPhoto store(PlaceVisit visit, User author, int position, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   PlaceVisitPhoto photo = new PlaceVisitPhoto();
   photo.visit = visit; photo.createdBy = author; photo.position = position; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
  public WhyFunVisitPhoto store(WhyFunVisit visit, User author, int position, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   WhyFunVisitPhoto photo = new WhyFunVisitPhoto();
   photo.visit = visit; photo.createdBy = author; photo.position = position; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
  public SpecialDateOccurrencePhoto store(SpecialDateOccurrence occurrence, User author, int position, MultipartFile upload) throws IOException {
   ImageData data = imageData(upload);
   SpecialDateOccurrencePhoto photo = new SpecialDateOccurrencePhoto();
   photo.occurrence = occurrence; photo.createdBy = author; photo.position = position; photo.imageBase64 = data.image(); photo.thumbnailBase64 = data.thumbnail(); photo.width = data.width(); photo.height = data.height(); photo.createdAt = Instant.now();
   return photo;
  }
 private ImageData imageData(MultipartFile upload) throws IOException {
  if (upload.getSize() > 10 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Máximo 10 MB");
   byte[] source = upload.getBytes();
   BufferedImage image = read(source);
   if (image == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida");
   image = orient(image, source);
   return new ImageData(Base64.getEncoder().encodeToString(render(image, 1600)),Base64.getEncoder().encodeToString(render(image, 480)),image.getWidth(),image.getHeight());
  }

  private BufferedImage read(byte[] source) throws IOException {
   if (!isWebp(source)) return ImageIO.read(new ByteArrayInputStream(source));
   Path input = Files.createTempFile("wherefood-", ".webp"), output = Files.createTempFile("wherefood-", ".png");
   try {
    Files.write(input, source);
    Files.delete(output);
    Process process = new ProcessBuilder("dwebp", input.toString(), "-o", output.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto no pudo procesarse"); }
    if (process.exitValue() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida");
    try (InputStream decoded = Files.newInputStream(output)) { return ImageIO.read(decoded); }
   } catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    throw new IOException("No se pudo procesar la imagen", exception);
   } finally {
    Files.deleteIfExists(input);
    Files.deleteIfExists(output);
   }
  }

  static boolean isWebp(byte[] source) {
   return source.length >= 12 && source[0] == 'R' && source[1] == 'I' && source[2] == 'F' && source[3] == 'F' && source[8] == 'W' && source[9] == 'E' && source[10] == 'B' && source[11] == 'P';
  }

  private BufferedImage orient(BufferedImage image, byte[] source) {
   try {
    ExifIFD0Directory exif = ImageMetadataReader.readMetadata(new ByteArrayInputStream(source)).getFirstDirectoryOfType(ExifIFD0Directory.class);
    int orientation = exif == null ? 1 : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION) == null ? 1 : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
    int width = image.getWidth(), height = image.getHeight();
    if (orientation == 1) return image;
    boolean sideways = orientation >= 5 && orientation <= 8;
    BufferedImage corrected = new BufferedImage(sideways ? height : width, sideways ? width : height, image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType());
    Graphics2D graphics = corrected.createGraphics();
    switch (orientation) {
     case 2 -> { graphics.translate(width, 0); graphics.scale(-1, 1); }
     case 3 -> { graphics.translate(width, height); graphics.rotate(Math.PI); }
     case 4 -> { graphics.translate(0, height); graphics.scale(1, -1); }
     case 5 -> { graphics.translate(height, 0); graphics.rotate(Math.PI / 2); graphics.scale(-1, 1); }
     case 6 -> { graphics.translate(height, 0); graphics.rotate(Math.PI / 2); }
     case 7 -> { graphics.translate(0, width); graphics.rotate(-Math.PI / 2); graphics.scale(-1, 1); }
     case 8 -> { graphics.translate(0, width); graphics.rotate(-Math.PI / 2); }
     default -> { return image; }
    }
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return corrected;
   } catch (Exception ignored) {
    return image;
   }
  }

 private byte[] render(BufferedImage image, int max) throws IOException {
  var out = new ByteArrayOutputStream();
  Thumbnails.of(image).size(max, max).outputFormat("webp").outputQuality(.82).toOutputStream(out);
  return out.toByteArray();
 }

 public String url(String base64) { return base64 == null ? null : "data:image/webp;base64," + base64; }
 public byte[] bytes(String base64) { return Base64.getDecoder().decode(base64); }
 private record ImageData(String image,String thumbnail,int width,int height) {}
}
