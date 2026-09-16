package com.stockflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, Map<String,String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI(), fields));
    }
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> domain(DomainException e, HttpServletRequest r) { return error(e.getStatus(), e.getCode(), e.getMessage(), Map.of(), r); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String,String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the request fields", fields, r);
    }
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> parameters(Exception e, HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid path or query parameter", Map.of(), r); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e, HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed JSON, unknown field, or invalid value type. Use the stock endpoint to change quantity.", Map.of(), r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest r) {
        // Database constraints remain the final protection against concurrent inserts/deletes.
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "A unique value already exists or a referenced resource is in use. Refresh and retry.", Map.of(), r);
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> concurrent(Exception e, HttpServletRequest r) { return error(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The resource is being modified. Refresh and retry.", Map.of(), r); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(NoResourceFoundException e, HttpServletRequest r) { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint was not found", Map.of(), r); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest r) { return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported", Map.of(), r); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media(HttpMediaTypeNotSupportedException e, HttpServletRequest r) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json", Map.of(), r); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
        log.error("Unexpected API failure for {}", r.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", Map.of(), r);
    }
}

