package com.exe.skillverse_backend.user_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSkillRequest {

    @NotNull(message = "Proficiency is required")
    @Min(value = 1, message = "Proficiency must be between 1 and 5")
    @Max(value = 5, message = "Proficiency must be between 1 and 5")
    private Integer proficiency; // 1-5 scale
}