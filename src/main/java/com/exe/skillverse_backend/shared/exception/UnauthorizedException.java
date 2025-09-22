package com.exe.skillverse_backend.shared.exception;

public class UnauthorizedException extends ApiException {
  public UnauthorizedException(String message) {
    super(ErrorCode.UNAUTHORIZED, message);
  }
}
