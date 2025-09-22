package com.exe.skillverse_backend.shared.exception;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
  private String code;        // ErrorCode.code
  private String message;     // mô tả ngắn
  private Integer status;     // http status
  private Instant timestamp;  // thời điểm lỗi
  private String path;        // request path
  private Map<String, Object> details; // lỗi field, context...
}
