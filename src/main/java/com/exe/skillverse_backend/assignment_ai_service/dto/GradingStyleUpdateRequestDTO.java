package com.exe.skillverse_backend.assignment_ai_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GradingStyleUpdateRequestDTO(
        @NotBlank(message = "Grading style không được trống")
        @Pattern(regexp = "^(STANDARD|STRICT|LENIENT)$", message = "Grading style phải là STANDARD, STRICT hoặc LENIENT")
        String style
) {
}