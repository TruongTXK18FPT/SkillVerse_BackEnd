package com.exe.skillverse_backend.question_bank_service.dto.request;

import com.exe.skillverse_backend.question_bank_service.entity.enums.QuestionBankSubmissionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionBankSubmissionRequest {

    @NotBlank(message = "Domain is required")
    private String domain;

    @NotBlank(message = "Industry is required")
    private String industry;

    @NotBlank(message = "Job role is required")
    private String jobRole;

    @NotBlank(message = "Skill name is required")
    private String skillName;

    private String title;
    private String description;
    private String difficultyDistribution;

    @NotNull(message = "Source is required")
    private QuestionBankSubmissionSource source;

    @Valid
    @NotEmpty(message = "At least one question is required")
    private List<CreateQuestionRequest> questions;
}
