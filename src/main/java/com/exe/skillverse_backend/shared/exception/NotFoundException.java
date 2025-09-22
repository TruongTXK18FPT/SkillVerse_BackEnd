package com.exe.skillverse_backend.shared.exception;

public class NotFoundException extends ApiException {
  public NotFoundException(String message) {
    super(ErrorCode.NOT_FOUND, message);
  }
}