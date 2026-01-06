package com.exe.skillverse_backend.shared.exception;

import lombok.Getter;
import java.util.List;

/**
 * Exception thrown when validation fails.
 * Contains a list of validation error messages.
 */
@Getter
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }
}
