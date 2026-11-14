package com.example.tasktracker.web;

import com.example.tasktracker.service.InvalidTaskException;
import com.example.tasktracker.service.TaskNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleNotFound(TaskNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** A rule the service enforces against stored state; same shape as Bean Validation failures. */
    @ExceptionHandler(InvalidTaskException.class)
    public ProblemDetail handleInvalidTask(InvalidTaskException ex) {
        return badRequest("Validation failed", Map.of(ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return badRequest("Validation failed", errors);
    }

    /**
     * Jackson could not turn the body into a request object. Map the cause onto the same
     * shape as {@link #handleValidation}; Jackson's own message is never echoed because it
     * exposes internal class names.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();

        // No cause: Spring rejected a zero-byte body before Jackson ran.
        // "No content to map": Jackson found only whitespace.
        if (cause == null || (cause instanceof MismatchedInputException mie
                && mie.getPath().isEmpty()
                && String.valueOf(mie.getOriginalMessage()).startsWith("No content to map"))) {
            return badRequest("Request body is required", Map.of());
        }

        if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = fieldPath(mie.getPath());
            Class<?> target = mie.getTargetType();
            String message = (mie instanceof InvalidFormatException && target != null && target.isEnum())
                    ? field + " must be one of " + Arrays.toString(target.getEnumConstants())
                    : field + " has an invalid value";
            return badRequest("Validation failed", Map.of(field, message));
        }

        return badRequest("Malformed JSON request body", Map.of());
    }

    /**
     * A query or path parameter could not be converted (e.g. {@code ?status=BLOCKED}).
     * Same shape as body validation failures; the conversion exception's message is not echoed.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Class<?> target = ex.getRequiredType();
        String message = (target != null && target.isEnum())
                ? name + " must be one of " + Arrays.toString(target.getEnumConstants())
                : name + " has an invalid value";
        return badRequest("Validation failed", Map.of(name, message));
    }

    /** No handler mapped for the path (Spring Boot raises this instead of a bare 404 page). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
    }

    /** Path exists but not for this method; keeps Spring's {@code Allow} header. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return withHeaders(ex, "Method " + ex.getMethod() + " is not supported for this resource");
    }

    /** Request body sent with a Content-Type no converter reads; keeps Spring's {@code Accept} header. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return withHeaders(ex, "Content-Type must be application/json");
    }

    /** The Accept header excludes JSON; the problem itself is still rendered as application/problem+json. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return withHeaders(ex, "Responses can only be produced as application/json");
    }

    /**
     * Anything not handled above. Other Spring MVC exceptions already carry a status and a
     * client-safe detail ({@link ErrorResponse}), so keep those. Everything else is a 500 whose
     * body never mentions the exception; the stack trace goes to the log only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(errorResponse.getBody());
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
    }

    private static ResponseEntity<ProblemDetail> withHeaders(ErrorResponse ex, String detail) {
        return ResponseEntity.status(ex.getStatusCode())
                .headers(ex.getHeaders())
                .body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), detail));
    }

    private static ProblemDetail badRequest(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String fieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }
}
