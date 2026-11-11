package com.futbolix.schedules.adapter.in.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.futbolix.schedules.application.CourtCatalogUnavailableException;
import com.futbolix.schedules.application.CourtNotFoundException;
import com.futbolix.schedules.application.ScheduleNotFoundException;
import com.futbolix.schedules.domain.InvalidScheduleException;
import com.futbolix.schedules.domain.InvalidScheduleTimeException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to the ErrorResponse of _shared.yaml. Never leaks internals (A05/A09). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidScheduleTimeException.class)
    ResponseEntity<ErrorResponse> notWholeHour(InvalidScheduleTimeException e) {
        return validation(List.of(new ErrorResponse.Detail(e.field(), e.getMessage())));
    }

    @ExceptionHandler(InvalidTimeFormatException.class)
    ResponseEntity<ErrorResponse> badTime(InvalidTimeFormatException e) {
        return validation(List.of(new ErrorResponse.Detail(e.field(), e.getMessage())));
    }

    @ExceptionHandler(InvalidScheduleException.class)
    ResponseEntity<ErrorResponse> invertedSchedule(InvalidScheduleException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCHEDULE", e.getMessage(),
                List.of(new ErrorResponse.Detail("closingTime", e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> badParameter(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new ErrorResponse.Detail(e.getName(), "Invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return validation(List.of(new ErrorResponse.Detail(e.getParameterName(), "Required")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return validation(List.of(new ErrorResponse.Detail(path(unknown), "Unknown field")));
        }
        if (cause instanceof InvalidFormatException invalid) {
            return validation(List.of(new ErrorResponse.Detail(path(invalid), "Invalid value")));
        }
        return validation(List.of(new ErrorResponse.Detail(null, "The body must be a JSON object")));
    }

    @ExceptionHandler(AdministratorInterceptor.AuthenticationRequiredException.class)
    ResponseEntity<ErrorResponse> unauthenticated() {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", List.of());
    }

    @ExceptionHandler(AdministratorInterceptor.AdministratorRequiredException.class)
    ResponseEntity<ErrorResponse> forbidden() {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Administrator authorization is required", List.of());
    }

    @ExceptionHandler({CourtNotFoundException.class, ScheduleNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> notFound() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(CourtCatalogUnavailableException.class)
    ResponseEntity<ErrorResponse> courtsUnavailable(CourtCatalogUnavailableException e) {
        log.warn("Courts service unavailable: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "The required service or dependency is temporarily unavailable", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The method is not allowed on this resource", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send the body as application/json", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected internal server error occurred", List.of());
    }

    private static ResponseEntity<ErrorResponse> validation(List<ErrorResponse.Detail> details) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request data", details);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                                       List<ErrorResponse.Detail> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, details, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private static String path(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
