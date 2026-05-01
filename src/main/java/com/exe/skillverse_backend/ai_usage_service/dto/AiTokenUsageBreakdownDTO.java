package com.exe.skillverse_backend.ai_usage_service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiTokenUsageBreakdownDTO {
    private List<AiTokenUsageBreakdownItemDTO> byFlowType;
    private List<AiTokenUsageBreakdownItemDTO> byProviderType;
    private List<AiTokenUsageBreakdownItemDTO> byModel;
    private List<AiTokenUsageBreakdownItemDTO> byStatus;
}
