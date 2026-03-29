package com.exe.skillverse_backend.question_bank_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankResponse {

    private Long id;
    private String domain;
    private String industry;
    private String jobRole;
    private String title;
    private String description;
    private String difficultyDistribution;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer activeQuestionCount;
    private Map<String, Long> difficultyBreakdown;
}
