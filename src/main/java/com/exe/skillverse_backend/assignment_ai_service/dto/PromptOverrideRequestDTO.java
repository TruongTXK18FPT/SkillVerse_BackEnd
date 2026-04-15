package com.exe.skillverse_backend.assignment_ai_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptOverrideRequestDTO(
        @NotBlank(message = "Prompt không được trống")
        @Size(max = 10000, message = "Prompt không được vượt quá 10000 ký tự")
        String prompt
) {
}