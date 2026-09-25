package com.wherefood.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public final class ProblemDetailsSupport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemDetailsSupport() {}

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String errorCode, String detail) {
        return response(status, errorCode, detail, Map.of());
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String errorCode, String detail,
            Map<String, ?> extensions) {
        String requestId = UUID.randomUUID().toString();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create("urn:uuid:" + requestId));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", requestId);
        extensions.forEach((key, value) -> problem.setProperty(key, value));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .header("X-Request-Id", requestId).body(problem);
    }

    public static void write(HttpServletResponse response, HttpStatus status, String errorCode, String detail,
            Long retryAfterSeconds) throws IOException {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", "urn:uuid:" + requestId);
        body.put("errorCode", errorCode);
        body.put("requestId", requestId);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Request-Id", requestId);
        if (retryAfterSeconds != null) response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSeconds)));
        JSON.writeValue(response.getWriter(), body);
    }
}
