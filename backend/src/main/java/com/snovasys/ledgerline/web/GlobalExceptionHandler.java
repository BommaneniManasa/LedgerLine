package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.exception.ApiError;
import com.snovasys.ledgerline.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into the same safe JSON shape. Clients never see stack traces, SQL or class names.
 * Unexpected errors are logged on the server with a reference id and returned as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for parameter '" + ex.getName() + "'",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String ref = UUID.randomUUID().toString();
        log.warn("Data integrity violation [ref={}]", ref, ex);
        return build(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data (ref " + ref + ")",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleLock(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Lock failure", ex);
        return build(HttpStatus.CONFLICT, "TRY_AGAIN", "The system is busy, please retry", request.getRequestURI(), null);
    }

    /** Let Spring Security's own handlers (entry point / access denied handler) deal with these. */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        String ref = UUID.randomUUID().toString();
        log.error("Unexpected error [ref={}]", ref, ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong (ref " + ref + ")", request.getRequestURI(), null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
                path(request), fields);
    }

    /** Every other Spring MVC error (bad JSON, missing header, wrong method, 404 ...) gets the same shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status == null ? "Request failed" : status.getReasonPhrase();
        return build(statusCode, "REQUEST_ERROR", reason, path(request), null);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }

    private static ResponseEntity<Object> build(HttpStatusCode status, String code, String message, String path,
                                                Map<String, String> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, path, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
