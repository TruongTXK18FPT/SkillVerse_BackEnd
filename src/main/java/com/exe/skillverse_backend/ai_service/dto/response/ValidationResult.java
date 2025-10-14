package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a validation result with severity level
 * Used for soft warning system in roadmap generation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResult {

    private ValidationSeverity severity;
    private String message;
    private String suggestedFix;
    private String field; // Which field has the issue (goal, duration, etc.)

    /**
     * Severity levels for validation
     */
    public enum ValidationSeverity {
        /**
         * Informational - just FYI, no action needed
         * Example: "Goal is broad, roadmap will be general overview"
         */
        INFO,

        /**
         * Warning - potential issue but generation can proceed
         * Example: "2 weeks may not be enough for this goal"
         */
        WARNING,

        /**
         * Error - critical issue, must be fixed before generation
         * Example: "IELTS score cannot exceed 9.0"
         */
        ERROR
    }

    /**
     * Quick check if this is an error (should block generation)
     */
    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }

    /**
     * Quick check if this is a warning (should show but allow generation)
     */
    public boolean isWarning() {
        return severity == ValidationSeverity.WARNING;
    }

    /**
     * Quick check if this is just info (minimal UX impact)
     */
    public boolean isInfo() {
        return severity == ValidationSeverity.INFO;
    }

    // Common validation result factory methods

    public static ValidationResult error(String field, String message, String suggestedFix) {
        return ValidationResult.builder()
                .severity(ValidationSeverity.ERROR)
                .field(field)
                .message(message)
                .suggestedFix(suggestedFix)
                .build();
    }

    public static ValidationResult warning(String field, String message, String suggestedFix) {
        return ValidationResult.builder()
                .severity(ValidationSeverity.WARNING)
                .field(field)
                .message(message)
                .suggestedFix(suggestedFix)
                .build();
    }

    public static ValidationResult info(String field, String message, String suggestedFix) {
        return ValidationResult.builder()
                .severity(ValidationSeverity.INFO)
                .field(field)
                .message(message)
                .suggestedFix(suggestedFix)
                .build();
    }
}
