package com.exe.skillverse_backend.shared.exception;

public class ForbiddenException extends ApiException {
  public ForbiddenException(String message) {
    super(ErrorCode.FORBIDDEN, message);
  }
}