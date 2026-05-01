package com.exe.skillverse_backend.ai_usage_service.controller;

import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageBreakdownDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageLogDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageQueryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageSummaryDTO;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageTimeSeriesDTO;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import com.exe.skillverse_backend.ai_usage_service.service.AdminAiTokenUsageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-token-usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class AdminAiTokenUsageController {

    private final AdminAiTokenUsageQueryService queryService;

    @GetMapping("/summary")
    public ResponseEntity<AiTokenUsageSummaryDTO> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) AiFlowType flowType,
            @RequestParam(required = false) AiProviderType providerType,
            @RequestParam(required = false) AiUsageStatus status) {

        AiTokenUsageQueryDTO query = new AiTokenUsageQueryDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setFlowType(flowType);
        query.setProviderType(providerType);
        query.setStatus(status);

        return ResponseEntity.ok(queryService.getSummary(query));
    }

    @GetMapping("/timeseries")
    public ResponseEntity<List<AiTokenUsageTimeSeriesDTO>> getTimeSeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) AiFlowType flowType,
            @RequestParam(required = false) AiProviderType providerType,
            @RequestParam(required = false) AiUsageStatus status) {

        AiTokenUsageQueryDTO query = new AiTokenUsageQueryDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setFlowType(flowType);
        query.setProviderType(providerType);
        query.setStatus(status);

        return ResponseEntity.ok(queryService.getTimeSeries(query));
    }

    @GetMapping("/breakdown")
    public ResponseEntity<AiTokenUsageBreakdownDTO> getBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) AiFlowType flowType,
            @RequestParam(required = false) AiProviderType providerType,
            @RequestParam(required = false) AiUsageStatus status) {

        AiTokenUsageQueryDTO query = new AiTokenUsageQueryDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setFlowType(flowType);
        query.setProviderType(providerType);
        query.setStatus(status);

        return ResponseEntity.ok(queryService.getBreakdown(query));
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<AiTokenUsageLogDTO>> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) AiFlowType flowType,
            @RequestParam(required = false) AiProviderType providerType,
            @RequestParam(required = false) AiUsageStatus status,
            @RequestParam(required = false) String modelName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AiTokenUsageQueryDTO query = new AiTokenUsageQueryDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setFlowType(flowType);
        query.setProviderType(providerType);
        query.setStatus(status);
        query.setModelName(modelName);
        query.setPage(page);
        query.setSize(size);

        return ResponseEntity.ok(queryService.getLogs(query));
    }
}
