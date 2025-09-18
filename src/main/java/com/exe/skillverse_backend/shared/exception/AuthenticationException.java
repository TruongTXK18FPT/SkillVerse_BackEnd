package com.exe.skillverse_backend.shared.exception;

/**
 * Custom exception for authentication-related errors
 */
public class AuthenticationException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public AuthenticationException(String message) {
        super(message);
        this.errorCode = "AUTH_ERROR";
        this.httpStatus = 401;
    }

    public AuthenticationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 401;
    }

    public AuthenticationException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}