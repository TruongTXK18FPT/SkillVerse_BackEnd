package com.exe.skillverse_backend.shared.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  // 4xx
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND"),
  CONFLICT(HttpStatus.CONFLICT, "CONFLICT"),
  VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED"),

  // 5xx
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");

  public final HttpStatus status;
  public final String code;

  ErrorCode(HttpStatus status, String code) {
    this.status = status;
    this.code = code;
  }
}
