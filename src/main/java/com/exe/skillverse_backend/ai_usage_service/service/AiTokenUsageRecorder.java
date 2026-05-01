package com.exe.skillverse_backend.ai_usage_service.service;

import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageRecordCommand;

public interface AiTokenUsageRecorder {
    void recordSuccess(AiTokenUsageRecordCommand command);
    void recordFailure(AiTokenUsageRecordCommand command);
}
