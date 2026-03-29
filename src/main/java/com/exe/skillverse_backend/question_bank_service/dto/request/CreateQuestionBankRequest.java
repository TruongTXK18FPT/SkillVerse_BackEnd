package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionBankRequest {

    @NotBlank(message = "Domain is required")
    private String domain;

    private String industry;
    private String jobRole;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String difficultyDistribution;
}
