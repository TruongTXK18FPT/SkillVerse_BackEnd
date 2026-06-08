package com.exe.skillverse_backend.ai_usage_service.dto;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageRecordCommand {
    private AiFlowType flowType;
    private AiProviderType providerType;
    private String modelName;
    private Long userId;
    private String relatedEntityType;
    private Long relatedEntityId;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private boolean estimated;
    private Long latencyMs;
    private String errorCode;
    private AiUsageStatus status;
    private String metadata;
}
