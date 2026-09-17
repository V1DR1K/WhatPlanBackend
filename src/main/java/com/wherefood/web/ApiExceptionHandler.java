package com.wherefood.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "CONFLICT", "El registro entra en conflicto con datos existentes.", null));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate(OptimisticLockingFailureException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "El registro fue modificado por otra operación.", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        return ResponseEntity.status(resolved)
                .body(ApiError.of(resolved, resolved.name(), exception.getReason() == null ? "No se pudo completar la solicitud." : exception.getReason(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage() == null ? "Valor inválido" : error.getDefaultMessage(), (first, ignored) -> first));
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisá los datos ingresados.", fields));
    }

    record ApiError(String type, String title, int status, String detail, Map<String, String> fields, Instant timestamp) {
        static ApiError of(HttpStatus status, String code, String detail, Map<String, String> fields) {
            return new ApiError("about:blank", code, status.value(), detail, fields, Instant.now());
        }
    }
}
