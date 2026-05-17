package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionBankRequest {

    @NotNull(message = "Domain is required")
    private Long domainId;

    @NotNull(message = "Job position is required")
    private Long jobPositionId;

    private Long skillId;

    private String domain;
    private String skillName;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String difficultyDistribution;
}
