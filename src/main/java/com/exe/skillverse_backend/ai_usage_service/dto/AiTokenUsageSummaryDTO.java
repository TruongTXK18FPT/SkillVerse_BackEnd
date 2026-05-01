package com.exe.skillverse_backend.ai_usage_service.dto;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTokenUsageSummaryDTO {
    private Long totalTokens;
    private Long promptTokens;
    private Long completionTokens;
    private Long estimatedTokens;
    private Long requestCount;
    private Long successCount;
    private Long failedCount;
    private Double averageLatencyMs;
    private AiFlowType topFlowType;
    private Long topFlowTypeTokens;
    private AiProviderType topProviderType;
    private Long topProviderTypeTokens;
    private LocalDateTime from;
    private LocalDateTime to;
}
