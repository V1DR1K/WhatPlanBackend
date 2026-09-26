package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LocalDateIdCursorTest {
    @Test
    void roundTripsDatedAndLegacyNullDatePositions() {
        LocalDateIdCursor dated = new LocalDateIdCursor(LocalDate.of(2026, 9, 26), 42);
        assertEquals(dated, LocalDateIdCursor.decode(dated.encode()));

        LocalDateIdCursor legacy = new LocalDateIdCursor(null, 7);
        assertEquals(legacy, LocalDateIdCursor.decode(legacy.encode()));
        assertNull(LocalDateIdCursor.decode(null));
    }

    @Test
    void rejectsMalformedNonCanonicalAndOutOfRangeCursors() {
        assertInvalid("not-a-cursor");
        assertInvalid(encodeRaw("2026-09-26:2") + "="); // non-canonical padding
        assertInvalid(encodeRaw("2026-02-30:2")); // impossible calendar date
        assertInvalid(encodeRaw("2026-09-26:2:3")); // extra component
        assertInvalid(encodeRaw("-:1") + "="); // padded null-date cursor
        assertInvalid(encodeRaw("2026-09-26:9223372036854775808")); // Long overflow
        assertInvalid(encodeRaw("2026-09-26:0")); // ID must be positive
        assertInvalid("A".repeat(65));
    }

    private static String encodeRaw(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void assertInvalid(String cursor) {
        assertThrows(ResponseStatusException.class, () -> LocalDateIdCursor.decode(cursor));
    }
}
