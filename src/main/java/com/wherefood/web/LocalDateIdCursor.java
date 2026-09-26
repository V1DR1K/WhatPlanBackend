package com.wherefood.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record LocalDateIdCursor(LocalDate date, long id) {
    private static final int MAX_ENCODED_LENGTH = 64;
    private static final String NULL_DATE = "-";

    LocalDateIdCursor {
        if (id <= 0) {
            throw new IllegalArgumentException("Cursor ID must be positive");
        }
    }

    static LocalDateIdCursor decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            String value = new String(decoded, StandardCharsets.US_ASCII);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encoded)) {
                throw invalidCursor();
            }
            String[] components = value.split(":", -1);
            if (components.length != 2 || !components[1].matches("[1-9][0-9]{0,18}")) {
                throw invalidCursor();
            }
            LocalDate date = NULL_DATE.equals(components[0]) ? null : LocalDate.parse(components[0]);
            long id = Long.parseLong(components[1]);
            if (date != null && !date.toString().equals(components[0])) {
                throw invalidCursor();
            }
            return new LocalDateIdCursor(date, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    String encode() {
        String value = (date == null ? NULL_DATE : date.toString()) + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static ResponseStatusException invalidCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido");
    }
}
