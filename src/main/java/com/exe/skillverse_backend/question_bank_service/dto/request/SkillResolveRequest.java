package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillResolveRequest {

    @NotBlank(message = "Skill name is required")
    private String skillName;
}
