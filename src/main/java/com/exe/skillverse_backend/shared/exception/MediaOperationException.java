package com.exe.skillverse_backend.shared.exception;

/**
 * Exception thrown when media operations fail
 */
public class MediaOperationException extends RuntimeException {
    
    public MediaOperationException(String message) {
        super(message);
    }
    
    public MediaOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}