package com.exe.skillverse_backend.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Handles various types of exceptions and returns appropriate error responses.
 */
@RestControllerAdvice
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
         * Handles authentication exceptions for login and auth errors.
         *
         * @param ex  the AuthenticationException
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthentication(
                        AuthenticationException ex, HttpServletRequest req) {
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
                                .message("File size exceeds the maximum allowed limit of 500MB")
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
        @ExceptionHandler(com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException.class)
        public ResponseEntity<ErrorResponse> handleUsageLimitExceeded(
                        com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException ex,
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
         * Fallback handler for unexpected exceptions.
         *
         * @param ex  the Exception
         * @param req the HTTP request
         * @return error response entity
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpected(
                        Exception ex, HttpServletRequest req) {
                var body = ErrorResponse.builder()
                                .code(ErrorCode.INTERNAL_ERROR.code)
                                .message(ex.getMessage() != null ? ex.getMessage() : "Unexpected error")
                                .status(ErrorCode.INTERNAL_ERROR.status.value())
                                .timestamp(Instant.now())
                                .path(req.getRequestURI())
                                .build();
                return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status).body(body);
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