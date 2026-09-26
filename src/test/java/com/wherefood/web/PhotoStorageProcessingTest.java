package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.wherefood.domain.Item;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PhotoStorageProcessingTest {
    @Test
    void processesSmallValidImageWithinConfiguredMemoryBudget() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 100, 100);

        var photo = storage.store(new Item(), new MockMultipartFile("file", "small.png", "image/png", png.toByteArray()));

        assertEquals(2, photo.width);
        assertEquals(2, photo.height);
        assertTrue(PhotoStorage.isWebp(storage.bytes(photo.imageBase64)));
        assertTrue(PhotoStorage.isWebp(storage.bytes(photo.thumbnailBase64)));
    }

    @Test
    void removesExifDescriptionWhenTranscodingImageAndThumbnail() throws Exception {
        String privateMarker = "PRIVATE_LOCATION_MARKER_55.123_-77.123";
        byte[] jpegWithExif = jpegWithExifDescription(privateMarker);
        var sourceMetadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(jpegWithExif));
        assertEquals(privateMarker, sourceMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class)
                .getDescription(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION));

        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 100, 100);
        var photo = storage.store(new Item(), uploadJpeg(jpegWithExif));

        assertFalse(new String(storage.bytes(photo.imageBase64), StandardCharsets.ISO_8859_1).contains(privateMarker));
        assertFalse(new String(storage.bytes(photo.thumbnailBase64), StandardCharsets.ISO_8859_1).contains(privateMarker));
    }

    @Test
    void rejectsTruncatedJpegAsInvalidInput() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encodedJpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", encodedJpeg);
        byte[] truncatedJpeg = Arrays.copyOf(encodedJpeg.toByteArray(), 24);
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 100, 100);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> storage.store(new Item(), uploadJpeg(truncatedJpeg)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsGifContentEvenWhenClientLabelsItAsPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encodedGif = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "gif", encodedGif));
        MultipartFile disguisedGif = new MockMultipartFile("file", "photo.png", "image/png", encodedGif.toByteArray());
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 100, 100);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> storage.store(new Item(), disguisedGif));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsOversizedExtendedWebpDimensionsBeforeLaunchingDecoder() {
        PhotoStorage storage = storage(50L * 1024 * 1024, 25_000_000L, 8_000, 2);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> storage.store(new Item(), upload(webp("VP8X", extendedHeader(4096, 4096)))));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test
    void rejectsLossyAndLosslessWebpCanvasDimensionsBeforeLaunchingDecoder() {
        PhotoStorage storage = storage(50L * 1024 * 1024, 25_000_000L, 8_000, 2);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE,
                assertThrows(ResponseStatusException.class,
                        () -> storage.store(new Item(), upload(webp("VP8 ", lossyHeader(4096, 4096)))))
                        .getStatusCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE,
                assertThrows(ResponseStatusException.class,
                        () -> storage.store(new Item(), upload(webp("VP8L", losslessHeader(4096, 4096)))))
                        .getStatusCode());
    }

    @Test
    void boundsDecodedImageMemoryIndependentlyOfPixelConfiguration() {
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 25_000_000L, 8_000);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> storage.store(new Item(), upload(webp("VP8X", extendedHeader(1024, 1024)))));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test
    void rejectsMalformedWebpBeforeLaunchingDecoder() {
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 25_000_000L, 8_000);
        byte[] malformed = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> storage.store(new Item(), upload(malformed)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsConcurrentDecodesAndReleasesSlotAfterProcessingFailure() throws Exception {
        PhotoStorage storage = storage(1024 * 1024, 1024 * 1024L, 100_000, 8_000, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MultipartFile firstUpload = mock(MultipartFile.class);
        when(firstUpload.isEmpty()).thenReturn(false);
        when(firstUpload.getSize()).thenReturn(1L);
        when(firstUpload.getBytes()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) throw new IOException("Timed out waiting for test release");
            return new byte[] {0};
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> assertThrows(ResponseStatusException.class,
                    () -> storage.store(new Item(), firstUpload)));
            org.junit.jupiter.api.Assertions.assertTrue(entered.await(2, TimeUnit.SECONDS));

            ResponseStatusException busy = assertThrows(ResponseStatusException.class,
                    () -> storage.store(new Item(), upload(new byte[] {0})));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());

            release.countDown();
            assertEquals(HttpStatus.BAD_REQUEST, first.get(2, TimeUnit.SECONDS).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class,
                            () -> storage.store(new Item(), upload(new byte[] {0}))).getStatusCode());
        } finally {
            release.countDown();
        }
    }

    @Test
    void keepsProcessingSlotUntilTimedOutDecoderHasTerminated() throws Exception {
        CountDownLatch decoderTerminationWaitEntered = new CountDownLatch(1);
        CountDownLatch allowDecoderTermination = new CountDownLatch(1);
        Process timedOutDecoder = mock(Process.class);
        when(timedOutDecoder.waitFor(1, TimeUnit.SECONDS)).thenReturn(false);
        when(timedOutDecoder.destroyForcibly()).thenReturn(timedOutDecoder);
        when(timedOutDecoder.waitFor()).thenAnswer(invocation -> {
            decoderTerminationWaitEntered.countDown();
            if (!allowDecoderTermination.await(2, TimeUnit.SECONDS)) {
                throw new InterruptedException("Timed out waiting for decoder termination");
            }
            return 137;
        });
        Process alreadyFinishedDecoder = mock(Process.class);
        when(alreadyFinishedDecoder.waitFor(1, TimeUnit.SECONDS)).thenReturn(true);
        when(alreadyFinishedDecoder.exitValue()).thenReturn(1);

        java.util.concurrent.atomic.AtomicInteger decoderStarts = new java.util.concurrent.atomic.AtomicInteger();
        PhotoStorage storage = new PhotoStorage(1024 * 1024, 1024 * 1024L, 100_000, 8_000, 1, 1,
                (input, output) -> decoderStarts.getAndIncrement() == 0 ? timedOutDecoder : alreadyFinishedDecoder);
        byte[] validWebpHeader = webp("VP8X", extendedHeader(1, 1));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var firstUpload = executor.submit(() -> assertThrows(ResponseStatusException.class,
                    () -> storage.store(new Item(), upload(validWebpHeader))));
            assertTrue(decoderTerminationWaitEntered.await(2, TimeUnit.SECONDS));

            ResponseStatusException busy = assertThrows(ResponseStatusException.class,
                    () -> storage.store(new Item(), upload(validWebpHeader)));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
            assertEquals(1, decoderStarts.get());

            allowDecoderTermination.countDown();
            assertEquals(HttpStatus.BAD_REQUEST, firstUpload.get(2, TimeUnit.SECONDS).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class,
                            () -> storage.store(new Item(), upload(validWebpHeader))).getStatusCode());
            assertEquals(2, decoderStarts.get(), "A later upload can start after the child process is reaped");
        } finally {
            allowDecoderTermination.countDown();
        }
    }

    @Test
    void keepsProcessingSlotUntilInterruptedDecoderHasTerminated() throws Exception {
        CountDownLatch decoderTerminationWaitEntered = new CountDownLatch(1);
        CountDownLatch allowDecoderTermination = new CountDownLatch(1);
        Process interruptedDecoder = mock(Process.class);
        when(interruptedDecoder.waitFor(1, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Simulated cancellation"));
        when(interruptedDecoder.isAlive()).thenReturn(true);
        when(interruptedDecoder.destroyForcibly()).thenReturn(interruptedDecoder);
        when(interruptedDecoder.waitFor()).thenAnswer(invocation -> {
            decoderTerminationWaitEntered.countDown();
            if (!allowDecoderTermination.await(2, TimeUnit.SECONDS)) {
                throw new InterruptedException("Timed out waiting for decoder termination");
            }
            return 137;
        });
        Process alreadyFinishedDecoder = mock(Process.class);
        when(alreadyFinishedDecoder.waitFor(1, TimeUnit.SECONDS)).thenReturn(true);
        when(alreadyFinishedDecoder.exitValue()).thenReturn(1);

        java.util.concurrent.atomic.AtomicInteger decoderStarts = new java.util.concurrent.atomic.AtomicInteger();
        PhotoStorage storage = new PhotoStorage(1024 * 1024, 1024 * 1024L, 100_000, 8_000, 1, 1,
                (input, output) -> decoderStarts.getAndIncrement() == 0 ? interruptedDecoder : alreadyFinishedDecoder);
        byte[] validWebpHeader = webp("VP8X", extendedHeader(1, 1));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var firstUpload = executor.submit(() -> {
                try {
                    storage.store(new Item(), upload(validWebpHeader));
                    throw new AssertionError("Interrupted image processing must fail");
                } catch (IOException expected) {
                    return Thread.currentThread().isInterrupted();
                }
            });
            assertTrue(decoderTerminationWaitEntered.await(2, TimeUnit.SECONDS));

            ResponseStatusException busy = assertThrows(ResponseStatusException.class,
                    () -> storage.store(new Item(), upload(validWebpHeader)));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
            assertEquals(1, decoderStarts.get());

            allowDecoderTermination.countDown();
            assertTrue(firstUpload.get(2, TimeUnit.SECONDS), "The interrupt flag is restored after the child exits");
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class,
                            () -> storage.store(new Item(), upload(validWebpHeader))).getStatusCode());
            assertEquals(2, decoderStarts.get(), "A later upload can start only after the child process is reaped");
        } finally {
            allowDecoderTermination.countDown();
        }
    }

    private static PhotoStorage storage(long maxDecodedBytes, long maxPixels, int maxDimension, int concurrency) {
        return storage(1024 * 1024, maxDecodedBytes, maxPixels, maxDimension, concurrency);
    }

    private static PhotoStorage storage(long maxUploadBytes, long maxDecodedBytes, long maxPixels,
            int maxDimension) {
        return storage(maxUploadBytes, maxDecodedBytes, maxPixels, maxDimension, 2);
    }

    private static PhotoStorage storage(long maxUploadBytes, long maxDecodedBytes, long maxPixels,
            int maxDimension, int concurrency) {
        return new PhotoStorage(maxUploadBytes, maxDecodedBytes, maxPixels, maxDimension, 1, concurrency);
    }

    private static MultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "photo.webp", "image/webp", bytes);
    }

    private static MultipartFile uploadJpeg(byte[] bytes) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }

    private static byte[] jpegWithExifDescription(String description) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream encodedJpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", encodedJpeg);

        byte[] descriptionBytes = description.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0});
        writeLittleEndian16(tiff, 1);
        writeLittleEndian16(tiff, ExifIFD0Directory.TAG_IMAGE_DESCRIPTION);
        writeLittleEndian16(tiff, 2); // TIFF ASCII
        writeLittleEndian32(tiff, descriptionBytes.length + 1);
        writeLittleEndian32(tiff, 26); // IFD0 header + one entry + next-IFD pointer
        writeLittleEndian32(tiff, 0); // No next IFD
        tiff.writeBytes(descriptionBytes);
        tiff.write(0);

        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
        app1.writeBytes(tiff.toByteArray());
        int segmentLength = app1.size() + 2;

        byte[] jpeg = encodedJpeg.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(jpeg, 0, 2); // SOI
        result.write(0xff);
        result.write(0xe1); // APP1
        result.write((segmentLength >> 8) & 0xff);
        result.write(segmentLength & 0xff);
        result.writeBytes(app1.toByteArray());
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }

    private static void writeLittleEndian16(ByteArrayOutputStream target, int value) {
        target.write(value & 0xff);
        target.write((value >> 8) & 0xff);
    }

    private static void writeLittleEndian32(ByteArrayOutputStream target, int value) {
        target.write(value & 0xff);
        target.write((value >> 8) & 0xff);
        target.write((value >> 16) & 0xff);
        target.write((value >> 24) & 0xff);
    }

    private static byte[] webp(String chunkName, byte[] payload) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeInt32(result, 4 + 8 + payload.length);
        result.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        result.writeBytes(chunkName.getBytes(StandardCharsets.US_ASCII));
        writeInt32(result, payload.length);
        result.writeBytes(payload);
        return result.toByteArray();
    }

    private static byte[] extendedHeader(int width, int height) {
        byte[] header = new byte[10];
        writeInt24(header, 4, width - 1);
        writeInt24(header, 7, height - 1);
        return header;
    }

    private static byte[] lossyHeader(int width, int height) {
        byte[] header = new byte[10];
        header[3] = (byte) 0x9d;
        header[4] = 0x01;
        header[5] = 0x2a;
        writeInt16(header, 6, width);
        writeInt16(header, 8, height);
        return header;
    }

    private static byte[] losslessHeader(int width, int height) {
        int widthMinusOne = width - 1;
        int heightMinusOne = height - 1;
        return new byte[] {
                0x2f,
                (byte) (widthMinusOne & 0x3f),
                (byte) (((widthMinusOne >> 6) & 0x3f) | ((heightMinusOne & 0x03) << 6)),
                (byte) ((heightMinusOne >> 2) & 0xff),
                (byte) ((heightMinusOne >> 10) & 0x0f),
        };
    }

    private static void writeInt16(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
    }

    private static void writeInt24(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
    }

    private static void writeInt32(ByteArrayOutputStream target, int value) {
        target.write(value & 0xff);
        target.write((value >> 8) & 0xff);
        target.write((value >> 16) & 0xff);
        target.write((value >> 24) & 0xff);
    }
}
