package io.github.tuck1297.kioskpos.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into RFC 9457 application/problem+json with a stable {@code code}
 * property, as described by the Problem schema in openapi.yaml.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_BASE = "https://kiosk-pos.example.com/problems/";
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(Cursor.InvalidCursorException.class)
    ResponseEntity<ProblemDetail> handleInvalidCursor(Cursor.InvalidCursorException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The cursor parameter is not valid. Use nextCursor from a previous page.",
                "VALIDATION_FAILED");
    }

    /**
     * The generated API interfaces are {@code @Validated}, so constraint violations on
     * query/path/header parameters (e.g. limit > 100) arrive here rather than through MVC.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", parameterName(v), "message", v.getMessage()))
                .sorted(Comparator.comparing(e -> e.get("field")))
                .toList();
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid.", "VALIDATION_FAILED");
        response.getBody().setProperty("errors", errors);
        return response;
    }

    /** Anything unexpected still comes back as problem+json, without leaking internals. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "Something went wrong. Please try again.", "INTERNAL_ERROR");
    }

    /** "listLocations.limit" -> "limit" */
    private static String parameterName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }

    /** Adds {@code type} and {@code code} to the problems Spring raises itself (400, 404, 405, 415...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            String code = codeFor(statusCode);
            problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')));
            problem.setProperty("code", code);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 412 -> "STALE_VERSION";
            case 428 -> "PRECONDITION_REQUIRED";
            default -> status.is4xxClientError() ? "VALIDATION_FAILED" : "INTERNAL_ERROR";
        };
    }
}
