package com.exe.skillverse_backend.ai_usage_service.service;

import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageBreakdownDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageLogDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageQueryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageSummaryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageTimeSeriesDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminAiTokenUsageQueryService {
    AiTokenUsageSummaryDTO getSummary(AiTokenUsageQueryDTO query);
    List<AiTokenUsageTimeSeriesDTO> getTimeSeries(AiTokenUsageQueryDTO query);
    AiTokenUsageBreakdownDTO getBreakdown(AiTokenUsageQueryDTO query);
    Page<AiTokenUsageLogDTO> getLogs(AiTokenUsageQueryDTO query);
}
