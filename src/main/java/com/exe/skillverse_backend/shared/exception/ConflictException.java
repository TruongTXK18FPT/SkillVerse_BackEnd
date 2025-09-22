package com.exe.skillverse_backend.shared.exception;

public class ConflictException extends ApiException {
  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }
}
