package com.exe.skillverse_backend.ai_usage_service.dto;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTokenUsageQueryDTO {
    private LocalDateTime from;
    private LocalDateTime to;
    private AiFlowType flowType;
    private AiProviderType providerType;
    private AiUsageStatus status;
    private String modelName;
    private int page = 0;
    private int size = 20;
}
