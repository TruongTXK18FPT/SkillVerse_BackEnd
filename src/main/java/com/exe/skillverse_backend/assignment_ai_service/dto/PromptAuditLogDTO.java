package com.exe.skillverse_backend.assignment_ai_service.dto;

import java.time.Instant;

public record PromptAuditLogDTO(
        Long id,
        Long assignmentId,
        String assignmentTitle,
        Long adminId,
        String adminName,
        String action,
        String beforeValue,
        String afterValue,
        Instant createdAt
) {
}