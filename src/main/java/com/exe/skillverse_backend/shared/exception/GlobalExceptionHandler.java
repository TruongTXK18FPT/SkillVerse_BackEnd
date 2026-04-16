package com.exe.skillverse_backend.shared.exception;

import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.shared.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for REST controllers.
 * Handles various types of exceptions and returns appropriate error responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        /**
         * Handles ApiException thrown intentionally by the application.
         *
         * @param ex  the ApiException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ErrorResponse> handleApiException(
                        ApiException ex, HttpServletRequest req) {
                var ec = ex.getErrorCode();
                String traceId = resolveTraceId(req);
                if (ec.status.is5xxServerError()) {
                        log.error("API exception [trace={}] path={} code={} status={} message={}",
                                        traceId,
                                        req.getRequestURI(),
                                        ec.code,
                                        ec.status.value(),
                                        ex.getMessage());
                } else {
                        log.warn("API exception [trace={}] path={} code={} status={} message={}",
                                        traceId,
                                        req.getRequestURI(),
                                        ec.code,
                                        ec.status.value(),
                                        ex.getMessage());
                }
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

        /**
         * Handles custom ValidationException with multiple error messages.
         *
         * @param ex  the ValidationException
         * @param req the HTTP request
         * @return error response entity with validation errors
         */
        @ExceptionHandler(ValidationException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(
                        ValidationException ex, HttpServletRequest req) {
                Map<String, Object> details = new HashMap<>();
                details.put("errors", ex.getErrors());
                var body = ErrorResponse.builder()
                                .code(ErrorCode.VALIDATION_FAILED.code)
                                .message(ex.getMessage())
                                .status(ErrorCode.VALIDATION_FAILED.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .details(details)
                                .build();
                return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status).body(body);
        }

        /**
         * Handles validation errors for @Valid on @RequestBody.
         *
         * @param ex  the MethodArgumentNotValidException
         * @param req the HTTP request
         * @return error response entity with field validation errors
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(
                        MethodArgumentNotValidException ex, HttpServletRequest req) {
                Map<String, Object> fieldErrors = new HashMap<>();
                ex.getBindingResult().getFieldErrors().forEach(
                                fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
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

        /**
         * Handles validation errors for @Valid on @ModelAttribute or @PathVariable.
         *
         * @param ex  the BindException
         * @param req the HTTP request
         * @return error response entity with field validation errors
         */
        @ExceptionHandler(BindException.class)
        public ResponseEntity<ErrorResponse> handleBind(
                        BindException ex, HttpServletRequest req) {
                Map<String, Object> fieldErrors = new HashMap<>();
                ex.getBindingResult().getFieldErrors().forEach(
                                fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
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

        /**
         * Handles IllegalArgumentException for bad request scenarios.
         *
         * @param ex  the IllegalArgumentException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(
                        IllegalArgumentException ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.BAD_REQUEST.code)
                                .message(ex.getMessage())
                                .status(ErrorCode.BAD_REQUEST.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.BAD_REQUEST.status).body(body);
        }

        /**
         * Handles malformed JSON / unreadable request payloads.
         * Returns 400 so clients receive actionable feedback instead of generic 500.
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
                        HttpMessageNotReadableException ex, HttpServletRequest req) {
                String message = "Request body is malformed or contains invalid field formats.";
                Throwable rootCause = ex.getMostSpecificCause();

                if (rootCause instanceof DateTimeParseException
                                || ex.getMessage().contains("LocalDateTime")) {
                        message = "Invalid date-time format for scheduledAt. Use yyyy-MM-dd'T'HH:mm:ss.";
                }

                log.warn("Invalid request payload at {}: {}", req.getRequestURI(), ex.getMessage());

                var body = ErrorResponse.builder()
                                .code(ErrorCode.BAD_REQUEST.code)
                                .message(message)
                                .status(ErrorCode.BAD_REQUEST.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.BAD_REQUEST.status).body(body);
        }

        /**
         * Handles custom authentication exceptions thrown by application services.
         *
         * @param ex  the custom AuthenticationException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleCustomAuthentication(
                        AuthenticationException ex,
                        HttpServletRequest req) {
                HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
                if (status == null) {
                        status = ErrorCode.UNAUTHORIZED.status;
                }

                String code = ex.getErrorCode();
                if (code == null || code.isBlank()) {
                        code = ErrorCode.UNAUTHORIZED.code;
                }

                var body = ErrorResponse.builder()
                                .code(code)
                                .message(ex.getMessage())
                                .status(status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(status).body(body);
        }

        /**
         * Handles Spring Security authentication exceptions.
         *
         * @param ex  the Spring Security AuthenticationException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleSpringAuthentication(
                        org.springframework.security.core.AuthenticationException ex,
                        HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.UNAUTHORIZED.code)
                                .message(ex.getMessage())
                                .status(ErrorCode.UNAUTHORIZED.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status).body(body);
        }

        /**
         * Handles account pending approval exceptions for mentor/recruiter cases.
         *
         * @param ex  the AccountPendingApprovalException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(AccountPendingApprovalException.class)
        public ResponseEntity<ErrorResponse> handleAccountPendingApproval(
                        AccountPendingApprovalException ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.FORBIDDEN.code)
                                .message(ex.getMessage())
                                .status(ErrorCode.FORBIDDEN.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(body);
        }

        /**
         * Handles file upload size exceeded exceptions.
         *
         * @param ex  the MaxUploadSizeExceededException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
                        MaxUploadSizeExceededException ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.BAD_REQUEST.code)
                                .message("File size exceeds the maximum allowed upload limit")
                                .status(ErrorCode.BAD_REQUEST.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.BAD_REQUEST.status).body(body);
        }

        /**
         * Handles usage limit exceeded exceptions.
         *
         * @param ex  the UsageLimitExceededException
         * @param req the HTTP request
         * @return error response entity with usage details
         */
        @ExceptionHandler(UsageLimitExceededException.class)
        public ResponseEntity<ErrorResponse> handleUsageLimitExceeded(
                        UsageLimitExceededException ex,
                        HttpServletRequest req) {
                Map<String, Object> details = new HashMap<>();
                details.put("featureType", ex.getFeatureType().name());
                details.put("featureName", ex.getFeatureType().getDisplayNameVi());
                if (ex.getCheckResult() != null) {
                        var result = ex.getCheckResult();
                        details.put("currentUsage", result.getCurrentUsage());
                        details.put("limit", result.getLimit());
                        details.put("remaining", result.getRemaining());
                        details.put("resetAt", result.getResetAt());
                        details.put("timeUntilReset", result.getTimeUntilReset());
                        details.put("upgradeMessage", "Nâng cấp lên Premium để tăng giới hạn sử dụng!");
                }
                var body = ErrorResponse.builder()
                                .code("USAGE_LIMIT_EXCEEDED")
                                .message(ex.getMessage())
                                .status(429) // Too Many Requests
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .details(details)
                                .build();
                return ResponseEntity.status(429).body(body);
        }

        /**
         * Handles AccessDeniedException for authorization errors.
         *
         * @param ex  the AccessDeniedException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(
                        AccessDeniedException ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.FORBIDDEN.code)
                                .message(ex.getMessage())
                                .status(ErrorCode.FORBIDDEN.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.FORBIDDEN.status).body(body);
        }

        /**
         * Handles missing endpoint/static resource routes.
         * Returns 404 instead of routing to generic 500 handler.
         */
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.NOT_FOUND.code)
                                .message("Resource not found")
                                .status(ErrorCode.NOT_FOUND.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.NOT_FOUND.status).body(body);
        }

        /**
         * Fallback handler for unexpected exceptions.
         * ✅ SECURITY: Never expose internal exception details to client
         *
         * @param ex  the Exception
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpected(
                        Exception ex, HttpServletRequest req) {
                // Log full exception server-side for debugging
                log.error("Unexpected error at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
                
                // Return generic message to client - never expose internal details
                var body = ErrorResponse.builder()
                                .code(ErrorCode.INTERNAL_ERROR.code)
                                .message("An unexpected error occurred. Please try again later.")
                                .status(ErrorCode.INTERNAL_ERROR.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(body);
        }

        private String resolveTraceId(HttpServletRequest req) {
                String requestId = req.getHeader("X-Request-Id");
                if (requestId != null && !requestId.isBlank()) {
                        return requestId;
                }
                String correlationId = req.getHeader("X-Correlation-Id");
                if (correlationId != null && !correlationId.isBlank()) {
                        return correlationId;
                }
                String mdcTraceId = MDC.get("traceId");
                if (mdcTraceId != null && !mdcTraceId.isBlank()) {
                        return mdcTraceId;
                }
                return "n/a";
        }

        /**
         * Converts details object to a Map.
         *
         * @param details the details object
         * @return map representation of details
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> asMap(Object details) {
                if (details == null) {
                        return new HashMap<>();
                }
                if (details instanceof Map<?, ?> m) {
                        return (Map<String, Object>) m;
                }
                return Map.of("info", details);
        }
}
