package com.exe.skillverse_backend.ai_usage_service.service.impl;

import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageBreakdownDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageBreakdownItemDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageLogDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageQueryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageSummaryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageTimeSeriesDTO;
import com.exe.skillverse_backend.ai_usage_service.entity.AiTokenUsageLog;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import com.exe.skillverse_backend.ai_usage_service.repository.AiTokenUsageLogRepository;
import com.exe.skillverse_backend.ai_usage_service.service.AdminAiTokenUsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAiTokenUsageQueryServiceImpl implements AdminAiTokenUsageQueryService {

    private final AiTokenUsageLogRepository repository;

    @Override
    public AiTokenUsageSummaryDTO getSummary(AiTokenUsageQueryDTO query) {
        LocalDateTime from = Optional.ofNullable(query.getFrom()).orElse(LocalDateTime.now().minusDays(7));
        LocalDateTime to = Optional.ofNullable(query.getTo()).orElse(LocalDateTime.now());

        // Use filtered queries that respect flowType, providerType, status filters
        Long totalTokens = repository.sumTotalTokensFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        Long promptTokens = repository.sumPromptTokensFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        Long completionTokens = repository.sumCompletionTokensFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        Long estimatedTokens = repository.sumEstimatedTokensFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        Long requestCount = repository.countFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        Double avgLatency = repository.averageLatencyFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());

        // Handle status filter consistency
        // If filtering by status, the filtered count IS that status count
        Long successCount;
        Long failedCount;
        if (query.getStatus() == AiUsageStatus.SUCCESS) {
            successCount = requestCount;
            failedCount = 0L;
        } else if (query.getStatus() == AiUsageStatus.FAILED) {
            successCount = 0L;
            failedCount = requestCount;
        } else {
            // No status filter - count both separately with other filters applied
            successCount = repository.countSuccessFiltered(from, to, query.getFlowType(), query.getProviderType());
            failedCount = repository.countFailedFiltered(from, to, query.getFlowType(), query.getProviderType());
        }

        // Top flow/provider use filtered queries now (consistent with breakdown)
        List<Object[]> flowTypeResults = repository.sumTokensByFlowTypeFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        AiFlowType topFlowType = null;
        Long topFlowTypeTokens = 0L;
        if (!flowTypeResults.isEmpty()) {
            topFlowType = (AiFlowType) flowTypeResults.get(0)[0];
            topFlowTypeTokens = (Long) flowTypeResults.get(0)[1];
        }

        List<Object[]> providerResults = repository.sumTokensByProviderTypeFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        AiProviderType topProviderType = null;
        Long topProviderTypeTokens = 0L;
        if (!providerResults.isEmpty()) {
            topProviderType = (AiProviderType) providerResults.get(0)[0];
            topProviderTypeTokens = (Long) providerResults.get(0)[1];
        }

        return AiTokenUsageSummaryDTO.builder()
                .totalTokens(totalTokens)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedTokens(estimatedTokens)
                .requestCount(requestCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .averageLatencyMs(avgLatency)
                .topFlowType(topFlowType)
                .topFlowTypeTokens(topFlowTypeTokens)
                .topProviderType(topProviderType)
                .topProviderTypeTokens(topProviderTypeTokens)
                .from(from)
                .to(to)
                .build();
    }

    @Override
    public List<AiTokenUsageTimeSeriesDTO> getTimeSeries(AiTokenUsageQueryDTO query) {
        LocalDateTime from = Optional.ofNullable(query.getFrom()).orElse(LocalDateTime.now().minusDays(7));
        LocalDateTime to = Optional.ofNullable(query.getTo()).orElse(LocalDateTime.now());

        boolean useHourly = ChronoUnit.HOURS.between(from, to) <= 48;

        // Use filtered queries to respect flowType, providerType, status filters
        List<Object[]> results;
        if (useHourly) {
            results = repository.hourlyTimeSeriesFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        } else {
            results = repository.dailyTimeSeriesFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        }

        List<AiTokenUsageTimeSeriesDTO> timeSeries = new ArrayList<>();
        for (Object[] row : results) {
            timeSeries.add(AiTokenUsageTimeSeriesDTO.builder()
                    .timestamp((LocalDateTime) row[1])
                    .flowType(row[0] != null ? ((AiFlowType) row[0]).name() : "ALL")
                    .promptTokens((Long) row[2])
                    .completionTokens((Long) row[3])
                    .totalTokens((Long) row[4])
                    .requestCount((Long) row[5])
                    .failedCount((Long) row[6])
                    .build());
        }

        return timeSeries;
    }

    @Override
    public AiTokenUsageBreakdownDTO getBreakdown(AiTokenUsageQueryDTO query) {
        LocalDateTime from = Optional.ofNullable(query.getFrom()).orElse(LocalDateTime.now().minusDays(7));
        LocalDateTime to = Optional.ofNullable(query.getTo()).orElse(LocalDateTime.now());

        // Use filtered query for total
        Long totalTokens = repository.sumTotalTokensFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus());
        if (totalTokens == null || totalTokens == 0) {
            totalTokens = 1L;
        }

        // Use filtered breakdown queries
        List<AiTokenUsageBreakdownItemDTO> byFlowType = buildBreakdown(
                repository.sumTokensByFlowTypeFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus()), totalTokens);
        List<AiTokenUsageBreakdownItemDTO> byProviderType = buildBreakdown(
                repository.sumTokensByProviderTypeFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus()), totalTokens);
        List<AiTokenUsageBreakdownItemDTO> byModel = buildBreakdown(
                repository.sumTokensByModelFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus()), totalTokens);
        List<AiTokenUsageBreakdownItemDTO> byStatus = buildBreakdown(
                repository.sumTokensByStatusFiltered(from, to, query.getFlowType(), query.getProviderType(), query.getStatus()), totalTokens);

        return AiTokenUsageBreakdownDTO.builder()
                .byFlowType(byFlowType)
                .byProviderType(byProviderType)
                .byModel(byModel)
                .byStatus(byStatus)
                .build();
    }

    private List<AiTokenUsageBreakdownItemDTO> buildBreakdown(List<Object[]> results, Long totalTokens) {
        List<AiTokenUsageBreakdownItemDTO> items = new ArrayList<>();
        for (Object[] row : results) {
            String key = row[0] != null ? row[0].toString() : "UNKNOWN";
            Long tokens = (Long) row[1];
            Long count = (Long) row[2];
            Double percentage = totalTokens > 0 ? (tokens * 100.0 / totalTokens) : 0.0;
            items.add(AiTokenUsageBreakdownItemDTO.builder()
                    .key(key)
                    .label(key)
                    .totalTokens(tokens)
                    .requestCount(count)
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build());
        }
        return items;
    }

    @Override
    public Page<AiTokenUsageLogDTO> getLogs(AiTokenUsageQueryDTO query) {
        LocalDateTime from = Optional.ofNullable(query.getFrom()).orElse(LocalDateTime.now().minusDays(7));
        LocalDateTime to = Optional.ofNullable(query.getTo()).orElse(LocalDateTime.now());
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        // Use single query with AND logic for all filters (flowType + providerType + status + modelName)
        Page<AiTokenUsageLog> logs = repository.findByFilters(
            from, to, query.getFlowType(), query.getProviderType(), query.getStatus(), query.getModelName(), pageable);

        return logs.map(this::toLogDTO);
    }

    private AiTokenUsageLogDTO toLogDTO(AiTokenUsageLog log) {
        return AiTokenUsageLogDTO.builder()
                .id(log.getId())
                .createdAt(log.getCreatedAt())
                .flowType(log.getFlowType())
                .providerType(log.getProviderType())
                .modelName(log.getModelName())
                .promptTokens(log.getPromptTokens())
                .completionTokens(log.getCompletionTokens())
                .totalTokens(log.getTotalTokens())
                .estimated(log.isEstimated())
                .status(log.getStatus())
                .latencyMs(log.getLatencyMs())
                .relatedEntityType(log.getRelatedEntityType())
                .relatedEntityId(log.getRelatedEntityId())
                .metadata(log.getMetadata())
                .build();
    }
}
