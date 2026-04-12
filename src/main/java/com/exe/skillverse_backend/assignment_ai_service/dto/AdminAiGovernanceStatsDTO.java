package com.exe.skillverse_backend.assignment_ai_service.dto;

public record AdminAiGovernanceStatsDTO(
        long aiEnabledAssignments,
        long trustAiAssignments,
        long customPromptAssignments,
        long strictAssignments,
        long lenientAssignments,
        long riskyTrustAssignments
) {
}
