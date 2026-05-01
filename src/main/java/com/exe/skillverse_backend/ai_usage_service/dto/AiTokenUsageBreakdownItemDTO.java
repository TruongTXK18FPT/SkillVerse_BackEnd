package com.exe.skillverse_backend.ai_usage_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageBreakdownItemDTO {
    private String key;
    private String label;
    private Long totalTokens;
    private Long requestCount;
    private Double percentage;
}
