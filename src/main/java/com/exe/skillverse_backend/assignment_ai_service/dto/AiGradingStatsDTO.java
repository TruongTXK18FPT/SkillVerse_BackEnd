package com.exe.skillverse_backend.assignment_ai_service.dto;

public record AiGradingStatsDTO(
    long totalAiGraded,
    long confirmed,
    long pending,
    long disputed,
    long totalAttempts,
    long lowConfidenceCount,
    double averageScoreDelta,
    long comparedSubmissionsCount
) {}
