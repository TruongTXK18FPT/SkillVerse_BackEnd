package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AiRoadmapService {
        List<ValidationResult> preValidateRequest(GenerateRoadmapRequest request);

        RoadmapResponse generateRoadmap(GenerateRoadmapRequest request, User user);

        List<ClarificationQuestion> generateClarificationQuestions(@Valid GenerateRoadmapRequest request);

        List<RoadmapSessionSummary> getAllRoadmaps();

        List<RoadmapSessionSummary> getUserRoadmaps(Long userId);

        RoadmapResponse getRoadmapById(Long sessionId, Long userId);

        ProgressResponse updateProgress(Long sessionId, Long userId, @Valid UpdateProgressRequest request);

        Map<String, Long> getModeCountsGlobal();

        Map<String, Long> getModeCountsForUser(Long userId);

        Map<String, Long> getModeCountsGlobalRange(Instant from, Instant to);

        Map<String, Long> getModeCountsForUserRange(Long userId, Instant from, Instant to);

        Map<String, Map<String, Long>> getModeCountsDaily(Instant from, Instant to);

        Map<String, Map<String, Long>> getModeCountsDailyForUser(Long userId, Instant from,
                        Instant to);

        Map<String, Map<String, Long>> getModeCountsWeekly(Instant from, Instant to);

        Map<String, Map<String, Long>> getModeCountsWeeklyForUser(Long userId, Instant from,
                        Instant to);

        Map<String, Map<String, Long>> getModeCountsMonthly(Instant from, Instant to);

        Map<String, Map<String, Long>> getModeCountsMonthlyForUser(Long userId, Instant from,
                        Instant to);
}
