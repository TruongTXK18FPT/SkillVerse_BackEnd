package com.exe.skillverse_backend.shared.exception;

public class BadRequestException extends ApiException {
  public BadRequestException(String message) {
    super(ErrorCode.BAD_REQUEST, message);
  }
}
