package com.exe.skillverse_backend.assignment_ai_service.dto;

import java.math.BigDecimal;

public record AdminAiAssignmentConfigDTO(
        Long assignmentId,
        String assignmentTitle,
        String courseName,
        String moduleName,
        Long mentorId,
        String mentorName,
        String submissionType,
        BigDecimal maxScore,
        Boolean aiGradingEnabled,
        Boolean trustAiEnabled,
        String gradingStyle,
        Boolean hasCustomPrompt,
        Integer promptLength,
        String promptPreview,
        long aiGradedSubmissions,
        long pendingMentorConfirmations,
        long disputedSubmissions,
        long lowConfidenceSubmissions,
        double averageScoreDelta
) {
}
