package com.exe.skillverse_backend.ai_usage_service.service.impl;

import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageRecordCommand;
import com.exe.skillverse_backend.ai_usage_service.entity.AiTokenUsageLog;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import com.exe.skillverse_backend.ai_usage_service.repository.AiTokenUsageLogRepository;
import com.exe.skillverse_backend.ai_usage_service.service.AiTokenUsageRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTokenUsageRecorderImpl implements AiTokenUsageRecorder {

    private final AiTokenUsageLogRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(AiTokenUsageRecordCommand command) {
        try {
            AiTokenUsageLog logEntry = AiTokenUsageLog.builder()
                    .flowType(command.getFlowType())
                    .providerType(command.getProviderType())
                    .modelName(command.getModelName())
                    .userId(command.getUserId())
                    .relatedEntityType(command.getRelatedEntityType())
                    .relatedEntityId(command.getRelatedEntityId())
                    .promptTokens(command.getPromptTokens())
                    .completionTokens(command.getCompletionTokens())
                    .totalTokens(command.getTotalTokens())
                    .estimated(command.isEstimated())
                    .status(AiUsageStatus.SUCCESS)
                    .latencyMs(command.getLatencyMs())
                    .metadata(command.getMetadata())
                    .build();

            repository.save(logEntry);
            log.debug("Recorded successful AI token usage: flow={}, provider={}, tokens={}",
                    command.getFlowType(), command.getProviderType(), command.getTotalTokens());
        } catch (Exception e) {
            log.warn("Failed to record successful AI token usage: flow={}, error={}",
                    command.getFlowType(), e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AiTokenUsageRecordCommand command) {
        try {
            AiTokenUsageLog logEntry = AiTokenUsageLog.builder()
                    .flowType(command.getFlowType())
                    .providerType(command.getProviderType())
                    .modelName(command.getModelName())
                    .userId(command.getUserId())
                    .relatedEntityType(command.getRelatedEntityType())
                    .relatedEntityId(command.getRelatedEntityId())
                    .promptTokens(command.getPromptTokens() != null ? command.getPromptTokens() : 0L)
                    .completionTokens(0L)
                    .totalTokens(command.getPromptTokens() != null ? command.getPromptTokens() : 0L)
                    .estimated(command.isEstimated())
                    .status(AiUsageStatus.FAILED)
                    .latencyMs(command.getLatencyMs())
                    .errorCode(command.getErrorCode())
                    .metadata(command.getMetadata())
                    .build();

            repository.save(logEntry);
            log.debug("Recorded failed AI token usage: flow={}, provider={}, error={}",
                    command.getFlowType(), command.getProviderType(), command.getErrorCode());
        } catch (Exception e) {
            log.warn("Failed to record failed AI token usage: flow={}, error={}",
                    command.getFlowType(), e.getMessage());
        }
    }
}
