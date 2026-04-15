package com.exe.skillverse_backend.assignment_ai_service.dto;

import jakarta.validation.constraints.NotNull;

public record AiEnabledUpdateRequestDTO(
        @NotNull(message = "enabled không được null")
        Boolean enabled
) {
}