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
    private Long domainId;
    private Long jobPositionId;
    private Long skillId;
    private String domain;
    private String domainName;
    private String jobPositionName;
    private String skillName;
    private String title;
    private String description;
    private String difficultyDistribution;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer activeQuestionCount;
    private Map<String, Long> difficultyBreakdown;
    private Map<String, Long> skillBreakdown;
}
