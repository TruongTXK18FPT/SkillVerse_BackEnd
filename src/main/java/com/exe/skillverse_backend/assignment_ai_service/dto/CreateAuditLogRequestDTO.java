package com.exe.skillverse_backend.assignment_ai_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAuditLogRequestDTO(
        @NotNull(message = "assignmentId không được null")
        Long assignmentId,

        @NotBlank(message = "action không được trống")
        String action,

        String beforeValue,
        String afterValue
) {
}