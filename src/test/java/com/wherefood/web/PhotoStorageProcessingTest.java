package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Item;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
