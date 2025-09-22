package com.exe.skillverse_backend.shared.exception;

public class AccessDeniedException extends ApiException {
    public AccessDeniedException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}