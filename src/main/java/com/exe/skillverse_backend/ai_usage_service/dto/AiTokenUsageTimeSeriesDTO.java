package com.exe.skillverse_backend.ai_usage_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTokenUsageTimeSeriesDTO {
    private LocalDateTime timestamp;
    private String flowType;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long requestCount;
    private Long failedCount;
}
