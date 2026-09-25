package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponseException;

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
    void mapsCoupleMediaQuotaToPayloadTooLargeWithoutLeakingDatabaseDetails() {
        SQLException sql = new SQLException("media_quota_exceeded", "23514");
        ConstraintViolationException database = new ConstraintViolationException(
                "media_quota_exceeded", sql, "chk_couples_media_quota");
        ResponseEntity<ProblemDetail> response = handler.conflict(
                new DataIntegrityViolationException("database internals", database));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("MEDIA_QUOTA_EXCEEDED", response.getBody().getProperties().get("errorCode"));
        assertEquals("La pareja alcanzó el límite de almacenamiento de fotos.", response.getBody().getDetail());
        assertFalse(response.getBody().getDetail().contains("database internals"));
    }

    @Test
    void mapsInternalErrorsToGenericDetail() {
        ResponseEntity<ProblemDetail> response = handler.internalError(new IllegalStateException("secret stack detail"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getProperties().get("errorCode"));
        assertFalse(response.getBody().getDetail().contains("secret stack detail"));
    }

    @Test
    void usesStableRateLimitCodeForStatusExceptions() {
        ResponseEntity<ProblemDetail> response = handler.status(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "internal limiter detail"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMITED", response.getBody().getProperties().get("errorCode"));
        assertEquals("Demasiadas solicitudes. Intentá nuevamente más tarde.", response.getBody().getDetail());
    }

    @Test
    void mapsFrameworkErrorsToSanitizedProblemDetails() {
        ResponseEntity<ProblemDetail> response = handler.frameworkError(new ErrorResponseException(HttpStatus.BAD_REQUEST));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("application/problem+json", response.getHeaders().getContentType().toString());
        assertEquals("INVALID_REQUEST", response.getBody().getProperties().get("errorCode"));
        assertEquals(400, response.getBody().getStatus());
    }
}
