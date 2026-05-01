package com.exe.skillverse_backend.ai_usage_service.dto;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTokenUsageLogDTO {
    private Long id;
    private LocalDateTime createdAt;
    private AiFlowType flowType;
    private AiProviderType providerType;
    private String modelName;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private boolean estimated;
    private AiUsageStatus status;
    private Long latencyMs;
    private String relatedEntityType;
    private Long relatedEntityId;
}
