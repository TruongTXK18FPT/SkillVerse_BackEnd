package com.exe.skillverse_backend.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ApiException do mình chủ động ném */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest req) {
        var ec = ex.getErrorCode();
        var body = ErrorResponse.builder()
                .code(ec.code)
                .message(ex.getMessage())
                .status(ec.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .details(asMap(ex.getDetails()))
                .build();
        return ResponseEntity.status(ec.status).body(body);
    }

    /* Validate @Valid trên @RequestBody – MethodArgumentNotValidException */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        var body = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.code)
                .message("Validation failed")
                .status(ErrorCode.VALIDATION_FAILED.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .details(fieldErrors)
                .build();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status).body(body);
    }

    /* Validate @Valid trên @ModelAttribute/@PathVariable – BindException */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex, HttpServletRequest req) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        var body = ErrorResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.code)
                .message("Validation failed")
                .status(ErrorCode.VALIDATION_FAILED.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .details(fieldErrors)
                .build();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status).body(body);
    }

    /* Authentication exceptions - Handle login/auth related errors */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        var body = ErrorResponse.builder()
                .code(ErrorCode.UNAUTHORIZED.code)
                .message(ex.getMessage())
                .status(ErrorCode.UNAUTHORIZED.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status).body(body);
    }

    /* Account pending approval - Handle mentor/recruiter approval cases */
    @ExceptionHandler(AccountPendingApprovalException.class)
    public ResponseEntity<ErrorResponse> handleAccountPendingApproval(AccountPendingApprovalException ex, HttpServletRequest req) {
        var body = ErrorResponse.builder()
                .code(ErrorCode.FORBIDDEN.code)
                .message(ex.getMessage())
                .status(ErrorCode.FORBIDDEN.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(body);
    }

    /* Fallback – lỗi không bắt được */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        var body = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR.code)
                .message(ex.getMessage() != null ? ex.getMessage() : "Unexpected error")
                .status(ErrorCode.INTERNAL_ERROR.status.value())
                .timestamp(Instant.now())
                .path(req.getRequestURI())
                .build();
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(body);
    }

    private Map<String, Object> asMap(Object details) {
        if (details == null)
            return null;
        if (details instanceof Map<?, ?> m)
            return (Map<String, Object>) m;
        return Map.of("info", details);
    }
}