package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsHiddenResourceStatusToProblemDetailsWithoutLeakingReason() {
        ResponseEntity<ProblemDetail> response = handler.status(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "resource 8472 belongs to another couple"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("application/problem+json", response.getHeaders().getContentType().toString());
        assertEquals("about:blank", response.getBody().getType().toString());
        assertEquals("No se encontró el recurso solicitado.", response.getBody().getDetail());
        assertNotNull(response.getBody().getProperties().get("requestId"));
        assertFalse(response.getBody().getDetail().contains("8472"));
    }

    @Test
    void mapsDatabaseConflictsToStableProblemCode() {
        ResponseEntity<ProblemDetail> response = handler.conflict(new DataIntegrityViolationException("private SQL detail"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().getProperties().get("errorCode"));
        assertFalse(response.getBody().getDetail().contains("private SQL detail"));
    }

    @Test
    void mapsInternalErrorsToGenericDetail() {
        ResponseEntity<ProblemDetail> response = handler.internalError(new IllegalStateException("secret stack detail"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getProperties().get("errorCode"));
        assertFalse(response.getBody().getDetail().contains("secret stack detail"));
    }
}
