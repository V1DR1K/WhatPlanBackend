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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class PhotoStorage {
 private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
 private static final long DEFAULT_MAX_DECODED_BYTES = 50L * 1024 * 1024;
 private static final long DEFAULT_MAX_PIXELS = 25_000_000L;
 private static final int DEFAULT_MAX_DIMENSION = 8_000;
 private static final long DEFAULT_PROCESS_TIMEOUT_SECONDS = 10;
 private static final int DEFAULT_MAX_CONCURRENT_PROCESSES = 2;
 private final long maxUploadBytes;
 private final long maxDecodedBytes;
 private final long maxPixels;
 private final int maxDimension;
 private final long processTimeoutSeconds;
 private final Semaphore processingSlots;
 private final DecoderProcessStarter decoderProcessStarter;

 public PhotoStorage() {
  this(DEFAULT_MAX_UPLOAD_BYTES, DEFAULT_MAX_DECODED_BYTES, DEFAULT_MAX_PIXELS, DEFAULT_MAX_DIMENSION,
          DEFAULT_PROCESS_TIMEOUT_SECONDS, DEFAULT_MAX_CONCURRENT_PROCESSES);
 }

 public PhotoStorage(
   long maxUploadBytes, long maxDecodedBytes, long maxPixels, int maxDimension, long processTimeoutSeconds) {
  this(maxUploadBytes, maxDecodedBytes, maxPixels, maxDimension, processTimeoutSeconds,
          DEFAULT_MAX_CONCURRENT_PROCESSES);
 }

 @org.springframework.beans.factory.annotation.Autowired
 public PhotoStorage(
   @org.springframework.beans.factory.annotation.Value("${app.images.max-upload-bytes:10485760}") long maxUploadBytes,
   @org.springframework.beans.factory.annotation.Value("${app.images.max-decoded-bytes:52428800}") long maxDecodedBytes,
   @org.springframework.beans.factory.annotation.Value("${app.images.max-pixels:25000000}") long maxPixels,
   @org.springframework.beans.factory.annotation.Value("${app.images.max-dimension:8000}") int maxDimension,
   @org.springframework.beans.factory.annotation.Value("${app.images.process-timeout-seconds:10}") long processTimeoutSeconds,
   @org.springframework.beans.factory.annotation.Value("${app.images.max-concurrent-processes:2}") int maxConcurrentProcesses) {
  this(maxUploadBytes, maxDecodedBytes, maxPixels, maxDimension, processTimeoutSeconds,
          maxConcurrentProcesses, PhotoStorage::startWebpDecoder);
 }

 PhotoStorage(long maxUploadBytes, long maxDecodedBytes, long maxPixels, int maxDimension,
         long processTimeoutSeconds, int maxConcurrentProcesses, DecoderProcessStarter decoderProcessStarter) {
  if (maxUploadBytes <= 0 || maxDecodedBytes <= 0 || maxPixels <= 0 || maxDimension <= 0
          || processTimeoutSeconds <= 0 || maxConcurrentProcesses <= 0 || decoderProcessStarter == null) {
   throw new IllegalArgumentException("Image processing limits must be positive");
  }
  this.maxUploadBytes = maxUploadBytes;
  this.maxDecodedBytes = maxDecodedBytes;
  this.maxPixels = maxPixels;
  this.maxDimension = maxDimension;
  this.processTimeoutSeconds = processTimeoutSeconds;
  this.processingSlots = new Semaphore(maxConcurrentProcesses);
  this.decoderProcessStarter = decoderProcessStarter;
 }

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
   if (upload == null || upload.isEmpty() || upload.getSize() > maxUploadBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo de imagen supera el límite permitido");
   if (!processingSlots.tryAcquire()) {
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El procesamiento de imágenes está ocupado. Intentá nuevamente en unos instantes.");
   }
   try {
    byte[] source = upload.getBytes();
    if (source.length > maxUploadBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo de imagen supera el límite permitido");
    BufferedImage image = read(source);
    if (image == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida");
    validateDimensions(image);
    image = orient(image, source);
    return new ImageData(Base64.getEncoder().encodeToString(render(image, 1600)),
            Base64.getEncoder().encodeToString(render(image, 480)), image.getWidth(), image.getHeight());
   } finally {
    processingSlots.release();
   }
  }

  private BufferedImage read(byte[] source) throws IOException {
    ImageIO.setUseCache(false);
    validateWebpDimensions(source);
    if (!isWebp(source)) {
     try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      if (input == null) return null;
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) return null;
      ImageReader reader = readers.next();
      try {
       reader.setInput(input, true, true);
       if (!isSupportedRasterFormat(reader.getFormatName())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El formato de imagen no está permitido");
       }
       validateDimensions(reader.getWidth(0), reader.getHeight(0));
       return reader.read(0);
      } catch (IOException exception) {
       throw invalidImage(exception);
      } finally {
       reader.dispose();
      }
     }
    }
   Path input = Files.createTempFile("wherefood-", ".webp"), output = Files.createTempFile("wherefood-", ".png");
   Process process = null;
   try {
    Files.write(input, source);
    Files.delete(output);
    process = decoderProcessStarter.start(input, output);
     if (!process.waitFor(processTimeoutSeconds, TimeUnit.SECONDS)) {
      terminateAndWait(process);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto no pudo procesarse");
     }
     if (process.exitValue() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida");
      if (!Files.exists(output) || Files.size(output) > maxDecodedBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen descomprimida supera el límite permitido");
      return readDecoded(output);
  } catch (InterruptedException exception) {
   if (process != null && process.isAlive()) terminateAndWait(process);
    Thread.currentThread().interrupt();
    throw new IOException("No se pudo procesar la imagen", exception);
   } finally {
    Files.deleteIfExists(input);
    Files.deleteIfExists(output);
   }
  }

  private static boolean isSupportedRasterFormat(String formatName) {
   return "jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)
           || "png".equalsIgnoreCase(formatName);
  }

  private void validateDimensions(BufferedImage image) {
   validateDimensions(image.getWidth(), image.getHeight());
  }

  private void validateDimensions(int width, int height) {
   long pixels = (long) width * height;
   if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
           || pixels > maxPixels || pixels > maxDecodedBytes / Integer.BYTES) {
    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Las dimensiones de la imagen superan el límite permitido");
   }
  }

  private static Process startWebpDecoder(Path input, Path output) throws IOException {
   return new ProcessBuilder("dwebp", input.toString(), "-o", output.toString())
           .redirectOutput(ProcessBuilder.Redirect.DISCARD)
           .redirectError(ProcessBuilder.Redirect.DISCARD)
           .start();
  }

  private static void terminateAndWait(Process process) {
   process.destroyForcibly();
   boolean interrupted = false;
   while (true) {
    try {
     process.waitFor();
     break;
    } catch (InterruptedException exception) {
     interrupted = true;
    }
   }
   if (interrupted) Thread.currentThread().interrupt();
  }

  @FunctionalInterface
  interface DecoderProcessStarter {
   Process start(Path input, Path output) throws IOException;
  }

  private static ResponseStatusException invalidImage(IOException cause) {
   return new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser una imagen válida", cause);
  }

  private void validateWebpDimensions(byte[] source) {
   if (!isWebp(source)) return;
   if (source.length < 20 || unsignedInt32(source, 4) != source.length - 8L) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto WebP no es válida");
   }

   long offset = 12;
   while (offset + 8 <= source.length) {
    int chunk = (int) offset;
    long chunkSize = unsignedInt32(source, chunk + 4);
    long payload = offset + 8;
    long end = payload + chunkSize;
    if (end > source.length) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto WebP no es válida");

    int width = 0;
    int height = 0;
    int data = (int) payload;
    if (matches(source, chunk, "VP8X") && chunkSize >= 10) {
     width = 1 + unsignedInt24(source, data + 4);
     height = 1 + unsignedInt24(source, data + 7);
    } else if (matches(source, chunk, "VP8 ") && chunkSize >= 10
            && source[data + 3] == (byte) 0x9d && source[data + 4] == 0x01 && source[data + 5] == 0x2a) {
     width = littleEndian16(source, data + 6) & 0x3fff;
     height = littleEndian16(source, data + 8) & 0x3fff;
    } else if (matches(source, chunk, "VP8L") && chunkSize >= 5 && source[data] == 0x2f) {
     width = 1 + (source[data + 1] & 0x3f) + ((source[data + 2] & 0x3f) << 8);
     height = 1 + ((source[data + 2] & 0xc0) >> 6) + ((source[data + 3] & 0xff) << 2)
             + ((source[data + 4] & 0x0f) << 10);
    }
    if (width > 0 && height > 0) {
     validateDimensions(width, height);
     return;
    }
    offset = end + (chunkSize & 1);
   }
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto WebP no contiene dimensiones reconocibles");
  }

  private static boolean matches(byte[] source, int offset, String value) {
   for (int index = 0; index < value.length(); index++) {
    if (source[offset + index] != (byte) value.charAt(index)) return false;
   }
   return true;
  }

  private static int littleEndian16(byte[] source, int offset) {
   return (source[offset] & 0xff) | ((source[offset + 1] & 0xff) << 8);
  }

  private static int unsignedInt24(byte[] source, int offset) {
   return (source[offset] & 0xff) | ((source[offset + 1] & 0xff) << 8) | ((source[offset + 2] & 0xff) << 16);
  }

  private static long unsignedInt32(byte[] source, int offset) {
   return (source[offset] & 0xffL) | ((source[offset + 1] & 0xffL) << 8)
           | ((source[offset + 2] & 0xffL) << 16) | ((source[offset + 3] & 0xffL) << 24);
  }

  private BufferedImage readDecoded(Path output) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(output)) {
      if (input == null) return null;
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) return null;
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        validateDimensions(reader.getWidth(0), reader.getHeight(0));
        return reader.read(0);
      } catch (IOException exception) {
        throw invalidImage(exception);
      } finally {
        reader.dispose();
      }
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
  public byte[] bytes(String base64) {
   if (base64 == null || base64.length() > ((maxDecodedBytes + 2) / 3) * 4) {
    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen almacenada supera el límite permitido");
   }
   try { return Base64.getDecoder().decode(base64); }
   catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La imagen almacenada no es válida"); }
  }
 private record ImageData(String image,String thumbnail,int width,int height) {}
}
