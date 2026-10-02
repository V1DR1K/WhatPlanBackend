package com.wherefood.web;

import com.wherefood.config.ProblemDetailsSupport;
import com.wherefood.config.RetryAfterResponseStatusException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception) {
        if (isCoupleMediaQuotaViolation(exception)) {
            return ProblemDetailsSupport.response(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_QUOTA_EXCEEDED",
                    "La pareja alcanzó el límite de almacenamiento de fotos.");
        }
        return ProblemDetailsSupport.response(HttpStatus.CONFLICT, "CONFLICT",
                "El registro entra en conflicto con datos existentes.");
    }

    private static boolean isCoupleMediaQuotaViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "chk_couples_media_quota".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> concurrentUpdate(OptimisticLockingFailureException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "El registro fue modificado por otra operación.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        String detail = switch (resolved) {
            case BAD_REQUEST, CONFLICT, UNPROCESSABLE_ENTITY -> exception.getReason() == null
                    ? "No se pudo completar la solicitud." : exception.getReason();
            case NOT_FOUND -> "No se encontró el recurso solicitado.";
            case UNAUTHORIZED -> "Autenticación requerida.";
            case FORBIDDEN -> "La solicitud no está permitida.";
            case TOO_MANY_REQUESTS -> "Demasiadas solicitudes. Intentá nuevamente más tarde.";
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "El servicio requerido no está disponible temporalmente.";
            default -> resolved.is5xxServerError() ? "Ocurrió un error interno." : "No se pudo completar la solicitud.";
        };
        ResponseEntity<ProblemDetail> response = ProblemDetailsSupport.response(resolved, stableErrorCode(resolved), detail);
        if (exception instanceof RetryAfterResponseStatusException retry) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                    .header("Retry-After", Long.toString(retry.retryAfterSeconds())).body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "La solicitud no está permitida.");
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> frameworkError(ErrorResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String detail = switch (resolved) {
            case BAD_REQUEST -> "La solicitud tiene un formato o parámetro inválido.";
            case NOT_FOUND -> "No se encontró el recurso solicitado.";
            case METHOD_NOT_ALLOWED -> "El método HTTP no está permitido para esta ruta.";
            case NOT_ACCEPTABLE -> "El formato de respuesta solicitado no está disponible.";
            case UNSUPPORTED_MEDIA_TYPE -> "El formato de contenido no está soportado.";
            case PAYLOAD_TOO_LARGE -> "La solicitud supera el tamaño permitido.";
            case UNAUTHORIZED -> "Autenticación requerida.";
            case FORBIDDEN -> "La solicitud no está permitida.";
            default -> resolved.is5xxServerError()
                    ? "Ocurrió un error interno. Intentá nuevamente más tarde."
                    : "No se pudo completar la solicitud.";
        };
        return ProblemDetailsSupport.response(resolved, stableErrorCode(resolved), detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Valor inválido" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ProblemDetailsSupport.response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Revisá los datos ingresados.", Map.of("errors", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Revisá los datos ingresados.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformedRequest(Exception ignored) {
        return ProblemDetailsSupport.response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "La solicitud tiene un formato o parámetro inválido.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "El método HTTP no está permitido para esta ruta.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMedia(HttpMediaTypeNotSupportedException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "El formato de contenido no está soportado.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> notAcceptable(HttpMediaTypeNotAcceptableException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "El formato de respuesta solicitado no está disponible.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE",
                "El archivo supera el tamaño permitido.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No se encontró el recurso solicitado.");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ProblemDetail> noHandler(NoHandlerFoundException ignored) {
        return ProblemDetailsSupport.response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No se encontró el recurso solicitado.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> internalError(Exception exception) {
        ResponseEntity<ProblemDetail> response = ProblemDetailsSupport.response(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Ocurrió un error interno. Intentá nuevamente más tarde.");
        Object requestId = response.getBody().getProperties().get("requestId");
        LOG.error("Unhandled API error; requestId={}, exceptionType={}", requestId, exception.getClass().getName());
        return response;
    }

    private static String stableErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            case CONFLICT -> "CONFLICT";
            case PAYLOAD_TOO_LARGE -> "UPLOAD_TOO_LARGE";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            case BAD_GATEWAY, GATEWAY_TIMEOUT -> "UPSTREAM_UNAVAILABLE";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : status.name();
        };
    }
}
